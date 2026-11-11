package com.maxassistant.app.automation;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DevicePlanTest {
    @Test public void strictSchemaRoundTripsAllKinds() throws Exception {
        for (DevicePlan original : new DevicePlan[]{DevicePlan.openApp("الحاسبة"), DevicePlan.facebookPost("مرحبا\nبالجميع 🌍"), DevicePlan.whatsappMessage("مروة", "أهلًا")}) {
            DevicePlan restored = DevicePlan.fromJson(original.toJson().toString());
            assertEquals(original.kind, restored.kind); assertEquals(original.app, restored.app);
            assertEquals(original.recipient, restored.recipient); assertEquals(original.body, restored.body);
        }
    }
    @Test public void acceptsRootPlannerExactSchema() throws Exception {
        DevicePlan plan = DevicePlan.fromJson("{\"kind\":\"FACEBOOK_POST\",\"app\":\"facebook\",\"recipient\":\"\",\"body\":\"مرحبا\"}");
        assertEquals(DevicePlan.Kind.FACEBOOK_POST, plan.kind);
    }
    @Test public void rejectsArbitraryExecutableStepsAndCoercion() throws Exception {
        JSONObject extra = DevicePlan.facebookPost("hello").toJson().put("steps", "click Send");
        assertInvalid(extra);
        assertInvalid(DevicePlan.facebookPost("hello").toJson().put("kind", "CLICK"));
        assertInvalid(DevicePlan.facebookPost("hello").toJson().put("app", "com.android.settings"));
        assertInvalid(DevicePlan.facebookPost("hello").toJson().put("recipient", "someone"));
        assertInvalid(DevicePlan.openApp("Facebook").toJson().put("body", "send this"));
        assertInvalid(DevicePlan.whatsappMessage("مروة", "hello").toJson().put("recipient", JSONObject.NULL));
        assertInvalid(DevicePlan.facebookPost("hello").toJson().put("body", 12));
        JSONObject missing = DevicePlan.facebookPost("hello").toJson(); missing.remove("body"); assertInvalid(missing);
    }
    @Test public void validatesNonemptyBoundedAndNonsecretTextShapes() {
        for (Runnable invalid : new Runnable[]{() -> DevicePlan.facebookPost(" "), () -> DevicePlan.whatsappMessage("", "hello"), () -> DevicePlan.whatsappMessage("a\nb", "hello"), () -> DevicePlan.facebookPost("a".repeat(4001)), () -> DevicePlan.facebookPost("bad\u0000text"), () -> DevicePlan.facebookPost("\uD800")}) {
            try { invalid.run(); fail("Invalid plan accepted"); } catch (IllegalArgumentException expected) { }
        }
    }
    private static void assertInvalid(JSONObject json) throws Exception {
        try { DevicePlan.fromJson(json); fail("Invalid plan accepted: " + json); } catch (IllegalArgumentException expected) { }
    }
}
