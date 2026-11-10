package com.maxassistant.app.ai;

/** Bundled llama.cpp JNI; model weights are downloaded independently. */
public final class LocalLlama {
    static { System.loadLibrary("maxllama"); }
    private LocalLlama(){}
    public static native void prepare();
    public static native String generate(String modelPath,String prompt,int maxTokens);
    public static native void cancel();
}
