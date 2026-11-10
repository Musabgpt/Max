#include <jni.h>
#include <llama.h>
#include <algorithm>
#include <atomic>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace {
std::mutex inference_mutex;
std::once_flag backend_once;
std::atomic<bool> cancelled{false};

// JNI's modified UTF-8 is unsuitable for supplementary characters such as emoji.
std::string utf8(JNIEnv *env, jstring value) {
    if (!value) throw std::invalid_argument("Model path and prompt are required");
    jclass string_class = env->FindClass("java/lang/String");
    if (!string_class) throw std::runtime_error("Unable to access string encoder");
    jmethodID get_bytes = env->GetMethodID(string_class, "getBytes", "(Ljava/lang/String;)[B");
    if (!get_bytes) throw std::runtime_error("Unable to access string encoder");
    jstring encoding = env->NewStringUTF("UTF-8");
    if (!encoding) throw std::runtime_error("Unable to allocate string encoding");
    auto bytes = static_cast<jbyteArray>(env->CallObjectMethod(value, get_bytes, encoding));
    if (!bytes || env->ExceptionCheck()) throw std::runtime_error("Unable to encode input");
    std::string result(static_cast<size_t>(env->GetArrayLength(bytes)), '\0');
    env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(result.size()), reinterpret_cast<jbyte *>(result.data()));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(encoding);
    env->DeleteLocalRef(string_class);
    return result;
}

jstring java_string(JNIEnv *env, const std::string &value) {
    auto bytes = env->NewByteArray(static_cast<jsize>(value.size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(value.size()), reinterpret_cast<const jbyte *>(value.data()));
    jclass string_class = env->FindClass("java/lang/String");
    if (!string_class) return nullptr;
    jmethodID constructor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
    if (!constructor) return nullptr;
    jstring encoding = env->NewStringUTF("UTF-8");
    if (!encoding) return nullptr;
    auto result = static_cast<jstring>(env->NewObject(string_class, constructor, bytes, encoding));
    env->DeleteLocalRef(encoding);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(string_class);
    return result;
}

bool should_abort(void *) { return cancelled.load(std::memory_order_relaxed); }
bool load_progress(float, void *) { return !cancelled.load(std::memory_order_relaxed); }
void throw_failure(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    if (exception) env->ThrowNew(exception, message);
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_maxassistant_app_ai_LocalLlama_generate(JNIEnv *env, jclass, jstring model_path,
                                               jstring prompt, jint max_tokens) {
    try {
        std::unique_lock<std::mutex> lock(inference_mutex, std::try_to_lock);
        if (!lock.owns_lock()) throw std::runtime_error("Local model is already generating; wait or cancel it");
        const auto path = utf8(env, model_path);
        const auto text = utf8(env, prompt);
        if (path.empty() || text.empty()) throw std::invalid_argument("Model path and prompt cannot be empty");
        if (text.size() > 64 * 1024) throw std::invalid_argument("Prompt exceeds 64 KiB");
        std::call_once(backend_once, [] { llama_backend_init(); });

        auto model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;
        model_params.progress_callback = load_progress;
        std::unique_ptr<llama_model, decltype(&llama_model_free)> model(
                llama_model_load_from_file(path.c_str(), model_params), llama_model_free);
        if (!model) throw std::runtime_error(cancelled ? "Generation cancelled" : "Cannot load model; download may be incomplete or memory insufficient");

        const llama_vocab *vocab = llama_model_get_vocab(model.get());
        int32_t count = -llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), nullptr, 0, true, true);
        if (count <= 0) throw std::runtime_error("Unable to tokenize prompt");
        std::vector<llama_token> tokens(static_cast<size_t>(count));
        count = llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), tokens.data(), count, true, true);
        if (count <= 0) throw std::runtime_error("Unable to tokenize prompt");
        tokens.resize(static_cast<size_t>(count));
        // Never cut ChatML role boundaries. The Java layer bounds whole messages.
        if (tokens.size() > 1536)
            throw std::runtime_error("Local prompt is too long; shorten your message or use NVIDIA");

        auto context_params = llama_context_default_params();
        context_params.n_ctx = 2048;
        context_params.n_batch = 512;
        context_params.n_ubatch = 128;
        context_params.n_threads = static_cast<int32_t>(std::min(4u, std::max(1u, std::thread::hardware_concurrency())));
        context_params.n_threads_batch = context_params.n_threads;
        context_params.abort_callback = should_abort;
        context_params.no_perf = true;
        std::unique_ptr<llama_context, decltype(&llama_free)> context(
                llama_init_from_model(model.get(), context_params), llama_free);
        if (!context) throw std::runtime_error("Not enough memory to initialize the local model");

        for (size_t offset = 0; offset < tokens.size(); offset += 512) {
            if (cancelled) throw std::runtime_error("Generation cancelled");
            const auto batch_size = static_cast<int32_t>(std::min(size_t(512), tokens.size() - offset));
            auto batch = llama_batch_get_one(tokens.data() + offset, batch_size);
            if (llama_decode(context.get(), batch) != 0)
                throw std::runtime_error(cancelled ? "Generation cancelled" : "Prompt evaluation failed");
        }

        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
                llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.65f));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        std::string response;
        const int limit = std::clamp(static_cast<int>(max_tokens), 1, 512);
        for (int step = 0; step < limit && !cancelled.load(std::memory_order_relaxed); ++step) {
            llama_token token = llama_sampler_sample(sampler.get(), context.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            char piece[256];
            const int32_t length = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
            if (length < 0) {
                std::vector<char> buffer(static_cast<size_t>(-length));
                const int32_t written = llama_token_to_piece(vocab, token, buffer.data(), -length, 0, false);
                if (written > 0) response.append(buffer.data(), static_cast<size_t>(written));
            } else {
                response.append(piece, static_cast<size_t>(length));
            }
            auto batch = llama_batch_get_one(&token, 1);
            if (llama_decode(context.get(), batch) != 0) {
                if (cancelled) break;
                throw std::runtime_error("Local model generation failed");
            }
        }
        if (cancelled) throw std::runtime_error("Generation cancelled");
        return java_string(env, response);
    } catch (const std::exception &error) {
        throw_failure(env, error.what());
        return nullptr;
    } catch (...) {
        throw_failure(env, "Unexpected native model error");
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_maxassistant_app_ai_LocalLlama_prepare(JNIEnv *, jclass) {
    cancelled.store(false, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_maxassistant_app_ai_LocalLlama_cancel(JNIEnv *, jclass) {
    cancelled.store(true, std::memory_order_relaxed);
}
