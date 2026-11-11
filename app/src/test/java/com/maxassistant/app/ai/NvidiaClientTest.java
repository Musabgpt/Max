package com.maxassistant.app.ai;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class NvidiaClientTest {
    private static final String KEY="nvapi-unit-test-only";
    private static final String ACTIVE=NvidiaClient.DEFAULT_MODEL;
    private static final class Call {
        final String method,url,key,body;
        Call(String m,String u,String k,String b){method=m;url=u;key=k;body=b;}
    }
    private static final class Fake implements NvidiaClient.Transport {
        final List<Call> calls=new ArrayList<>();
        final Deque<Object> results=new ArrayDeque<>();
        boolean cancelled;
        Fake respond(int code,String body){results.add(new NvidiaClient.Response(code,body));return this;}
        Fake fail(String message){results.add(new IOException(message));return this;}
        public NvidiaClient.Response send(String method,String url,String key,String body) throws IOException {
            calls.add(new Call(method,url,key,body));
            Object value=results.remove();if(value instanceof IOException)throw (IOException)value;return (NvidiaClient.Response)value;
        }
        public void cancel(){cancelled=true;}
    }
    private static JSONArray messages(){try{return new JSONArray().put(new JSONObject().put("role","user").put("content","مرحبا"));}catch(org.json.JSONException e){throw new AssertionError(e);}}
    private static String catalog(String... ids){try{JSONArray data=new JSONArray();for(String id:ids)data.put(new JSONObject().put("id",id));return new JSONObject().put("data",data).toString();}catch(org.json.JSONException e){throw new AssertionError(e);}}
    private static String answer(String text){try{return new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",text)))).toString();}catch(org.json.JSONException e){throw new AssertionError(e);}}
    private static IOException fails(NvidiaClient client,String model) {
        try{client.complete(model,"",messages());fail("Expected controlled error");return null;}catch(IOException e){return e;}
    }

    @Test public void oldSavedDefaultMigratesWithoutCallingRetiredEndpoint() throws Exception {
        Fake f=new Fake().respond(200,catalog(ACTIVE,"nvidia/nemotron-3.5-embed-1b")).respond(200,answer("أهلًا"));
        NvidiaClient.Result r=new NvidiaClient(KEY,f).complete(NvidiaClient.LEGACY_MODEL,"",messages());
        assertEquals("أهلًا",r.text);assertEquals(ACTIVE,r.model);assertTrue(r.switched);
        assertEquals(2,f.calls.size());assertEquals("GET",f.calls.get(0).method);assertNull(f.calls.get(0).key);
        assertEquals(ACTIVE,new JSONObject(f.calls.get(1).body).getString("model"));assertEquals(KEY,f.calls.get(1).key);
    }
    @Test public void goneCustomModelRefreshesCatalogAndRetriesSameConversation() throws Exception {
        Fake f=new Fake().respond(410,"{\"detail\":\"retired\"}").respond(200,catalog(ACTIVE)).respond(200,answer("تم"));
        NvidiaClient.Result r=new NvidiaClient(KEY,f).complete("example/retired-instruct","",messages());
        assertTrue(r.switched);assertEquals(ACTIVE,r.model);assertEquals(3,f.calls.size());
        JSONObject first=new JSONObject(f.calls.get(0).body),retry=new JSONObject(f.calls.get(2).body);
        assertEquals(first.getJSONArray("messages").toString(),retry.getJSONArray("messages").toString());
        assertEquals(NvidiaClient.CHAT_URL,f.calls.get(2).url);
    }
    @Test public void retiredAlternativesStopAfterTwoInferenceAttempts() {
        Fake f=new Fake().respond(200,catalog(ACTIVE,"nvidia/nemotron-3.5-bolt-e4b")).respond(410,"gone").respond(410,"gone");
        IOException e=fails(new NvidiaClient(KEY,f),"auto");assertTrue(e.getMessage().contains("410"));assertEquals(3,f.calls.size());
    }
    @Test public void authAndQuotaErrorsAreNotRetriedOrCalledRetirement() {
        for(int code:new int[]{401,403,429}){
            Fake f=new Fake().respond(code,KEY);IOException e=fails(new NvidiaClient(KEY,f),"example/custom-instruct");
            assertEquals(1,f.calls.size());assertFalse(e.getMessage().contains(KEY));
            if(code==403)assertTrue(e.getMessage().contains("Public API Endpoints"));
            if(code==429)assertTrue(e.getMessage().contains("حد"));
        }
    }
    @Test public void validExplicitModelIsPreservedWithoutCatalogCall() throws Exception {
        Fake f=new Fake().respond(200,answer("OK"));NvidiaClient.Result r=new NvidiaClient(KEY,f).complete("example/custom-instruct","",messages());
        assertFalse(r.switched);assertEquals("example/custom-instruct",r.model);assertEquals(1,f.calls.size());
    }
    @Test public void discoveryExcludesEmbeddingGuardAndUntrustedIds() throws Exception {
        String eligible="qwen/qwen3-next-80b-a3b-instruct";
        Fake f=new Fake().respond(200,catalog("nvidia/nemotron-3.5-embed-1b","nvidia/nemotron-3.5-content-safety","nvidia/nemotron-parse","https://evil.test/model",eligible)).respond(200,answer("أهلًا"));
        assertEquals(eligible,new NvidiaClient(KEY,f).complete("auto","",messages()).model);
    }
    @Test public void emptyChatCatalogDoesNotSendPrompt() {
        Fake f=new Fake().respond(200,catalog("nvidia/nemotron-3.5-embed-1b"));IOException e=fails(new NvidiaClient(KEY,f),"auto");
        assertTrue(e.getMessage().contains("دردشة"));assertEquals(1,f.calls.size());
    }
    @Test public void temporaryCatalogFailureUsesLastWorkingModel() throws Exception {
        Fake f=new Fake().respond(503,"").respond(200,answer("OK"));
        assertEquals(ACTIVE,new NvidiaClient(KEY,f).complete("auto",ACTIVE,messages()).model);
    }
    @Test public void thinkingDisabledForNemotronAndPrivateReasoningNeverDisplayed() throws Exception {
        Fake f=new Fake().respond(200,catalog(ACTIVE)).respond(200,answer("الجواب النهائي"));
        assertEquals("الجواب النهائي",new NvidiaClient(KEY,f).complete("auto","",messages()).text);
        JSONObject request=new JSONObject(f.calls.get(1).body);assertFalse(request.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"));assertFalse(request.getBoolean("stream"));
        Fake reason=new Fake().respond(200,"{\"choices\":[{\"message\":{\"content\":null,\"reasoning_content\":\"PRIVATE_REASONING\"}}]}");
        IOException e=fails(new NvidiaClient(KEY,reason),"example/custom-instruct");assertFalse(e.getMessage().contains("PRIVATE_REASONING"));
    }
    @Test public void cancellationBeforeStartDoesNotSendAnyRequest() {
        Fake f=new Fake();NvidiaClient c=new NvidiaClient(KEY,f);c.cancel();IOException e=fails(c,"auto");
        assertTrue(e.getMessage().contains("إيقاف"));assertTrue(f.cancelled);assertTrue(f.calls.isEmpty());
    }
    @Test public void malformedAndNetworkErrorsDoNotLeakKeyOrRetry() {
        Fake f=new Fake().fail("Invalid header: "+KEY);assertFalse(fails(new NvidiaClient(KEY,f),"example/custom-instruct").getMessage().contains(KEY));assertEquals(1,f.calls.size());
        Fake broken=new Fake().respond(200,"not-json "+KEY);assertFalse(fails(new NvidiaClient(KEY,broken),"example/custom-instruct").getMessage().contains(KEY));assertEquals(1,broken.calls.size());
    }
    @Test public void choiceMigrationPreservesCustomAndRejectsInvalidInputs() {
        assertEquals("auto",NvidiaClient.normalizeChoice(NvidiaClient.LEGACY_MODEL));assertEquals("auto",NvidiaClient.normalizeChoice(""));assertEquals("example/custom-instruct",NvidiaClient.normalizeChoice("example/custom-instruct"));
        Fake f=new Fake();assertNotNull(fails(new NvidiaClient("nvapi-\nPRIVATE",f),"auto"));assertTrue(f.calls.isEmpty());
        Fake badId=new Fake();assertNotNull(fails(new NvidiaClient(KEY,badId),"https://attacker.test"));assertTrue(badId.calls.isEmpty());
    }
    @Test public void literalNullIsAControlledMissingAnswer() {
        Fake f=new Fake().respond(200,answer("null"));
        IOException error=fails(new NvidiaClient(KEY,f),"example/custom-instruct");
        assertTrue(error.getMessage().contains("جواب"));assertEquals(1,f.calls.size());
    }
    @Test public void lengthTruncatedEmptyAnswerGetsOneLargerBudgetRetry() throws Exception {
        Fake f=new Fake().respond(200,"{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":null,\"reasoning_content\":\"PRIVATE\"}}]}").respond(200,answer("كود مكتمل"));
        NvidiaClient.Result result=new NvidiaClient(KEY,f).complete("example/custom-instruct","",messages());
        assertEquals("كود مكتمل",result.text);assertEquals(2,f.calls.size());
        JSONObject before=new JSONObject(f.calls.get(0).body),after=new JSONObject(f.calls.get(1).body);
        assertTrue(after.getInt("max_tokens")>before.getInt("max_tokens"));assertTrue(after.getInt("max_tokens")<=8192);
        assertEquals(before.getJSONArray("messages").toString(),after.getJSONArray("messages").toString());
    }
    @Test public void contentPartsExtractOnlyFinalText() throws Exception {
        Fake f=new Fake().respond(200,"{\"choices\":[{\"message\":{\"content\":[{\"type\":\"reasoning\",\"text\":\"PRIVATE\"},{\"type\":\"text\",\"text\":\"أهلًا\"}]}}]}");
        assertEquals("أهلًا",new NvidiaClient(KEY,f).complete("example/custom-instruct","",messages()).text);
    }
}
