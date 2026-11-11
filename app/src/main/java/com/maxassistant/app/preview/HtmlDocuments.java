package com.maxassistant.app.preview;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded extraction and self-contained HTML documents used by the local preview. */
public final class HtmlDocuments {
    public static final int MAX_HTML_CHARS = 200_000;
    private static final int MAX_RESPONSE_CHARS = MAX_HTML_CHARS + 4096;
    private static final Pattern ROOT = Pattern.compile("(?i)<html(?=[\\s>])[^>]*>");
    private static final Pattern ROOT_END = Pattern.compile("(?i)</html\\s*>");
    private static final Pattern DOCTYPE = Pattern.compile("(?i)<!doctype\\s+html[^>]*>\\s*");
    private static final Pattern FENCE = Pattern.compile("(?im)^ {0,3}(`{3,}|~{3,})[ \\t]*(?:html|htm)[ \\t]*\\r?\\n");
    private static final Pattern FRAGMENT = Pattern.compile("(?is)^<(div|main|section|article|header|footer|nav|button|form|table|ul|ol|p|h[1-6]|canvas|svg|style|script|body|head|input|img|label|textarea|select|span)(?=[\\s/>])[^>]*>.*");
    private HtmlDocuments() { }

    /** Extracts actual HTML; never treats an empty/sentinel response as runnable code. */
    public static String extract(String response) {
        if (response == null || response.length() > MAX_RESPONSE_CHARS)
            throw invalid();
        String value = response.trim();
        if (value.isEmpty()) throw invalid();
        Matcher fence = FENCE.matcher(value);
        if (fence.find()) {
            String marker = fence.group(1);
            Pattern closing = Pattern.compile("(?m)^ {0,3}" + Pattern.quote(marker) + "[" + marker.charAt(0) + "]*[ \\t]*\\r?$");
            Matcher end = closing.matcher(value);
            if (!end.find(fence.end())) throw invalid();
            String candidate = value.substring(fence.end(), end.start()).trim();
            String document = completeDocument(candidate);
            if (document != null) return bounded(document);
            if (!completeFragment(candidate)) throw invalid();
            return bounded(candidate);
        }
        String document = completeDocument(value);
        if (document == null && completeFragment(value)) document = value;
        if (document == null) throw invalid();
        return bounded(document);
    }

    public static boolean looksLikeHtml(String response) {
        try { extract(response); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private static String completeDocument(String value) {
        Matcher start = ROOT.matcher(value);
        if (!start.find()) return null;
        int beginning = start.start();
        Matcher doctype = DOCTYPE.matcher(value);
        while (doctype.find() && doctype.end() <= beginning) {
            if (value.substring(doctype.end(), beginning).trim().isEmpty()) beginning = doctype.start();
        }
        Matcher end = ROOT_END.matcher(value);
        end.region(start.end(), value.length());
        int ending = -1;
        while (end.find()) ending = end.end();
        return ending < 0 ? null : value.substring(beginning, ending);
    }

    private static boolean completeFragment(String candidate) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.contains("<html") || lower.contains("<!doctype")) return false;
        Matcher root = FRAGMENT.matcher(candidate);
        if (!root.matches()) return false;
        String tag = root.group(1).toLowerCase(Locale.ROOT);
        return "input".equals(tag) || "img".equals(tag)
            || Pattern.compile("(?i)</" + tag + "\\s*>").matcher(candidate).find();
    }

    private static String bounded(String document) {
        if (document.isEmpty() || document.length() > MAX_HTML_CHARS) throw invalid();
        return document;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("لم أجد كود HTML مكتملًا ضمن الحد المسموح. اطلب صفحة HTML كاملة ثم جرّب المعاينة.");
    }

    /** A complete offline calculator; arithmetic is parsed with precedence, never eval(). */
    public static String calculator() {
        return """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>آلة حاسبة · MAX</title>
            <style>
            *{box-sizing:border-box}body{margin:0;padding:20px;font-family:system-ui,sans-serif;background:#10151f;color:#eef6ff;min-height:100vh;display:grid;place-items:center}
            main{width:min(100%,380px);background:#192231;border:1px solid #334256;border-radius:24px;padding:22px;box-shadow:0 14px 40px #0005}
            h1{font-size:23px;margin:0 0 8px}.note{color:#b5c5d9;font-size:13px;margin:0 0 20px}.screen{background:#0b111b;border-radius:14px;padding:16px;overflow-wrap:anywhere}
            #display{direction:ltr;display:block;min-height:54px;font-size:36px;text-align:end;font-variant-numeric:tabular-nums}#message{min-height:22px;font-size:13px;color:#b5c5d9;margin-top:7px}
            .keys{direction:ltr;display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-top:18px}button{border:0;border-radius:12px;background:#29384c;color:#f5f8ff;font-size:23px;min-height:58px;touch-action:manipulation;cursor:pointer}button:active{filter:brightness(1.3)}button:focus-visible{outline:3px solid #fff}.op{color:#7fe4cb;background:#223d43}.clear{color:#ffd1cf;background:#53333b}.equals{background:#79e1c3;color:#102b27;grid-column:span 2}.wide{grid-column:span 2}
            </style>
            </head>
            <body><main>
            <h1>آلة حاسبة</h1><p class="note">تعمل داخل MAX بدون إنترنت</p>
            <div class="screen"><output id="display" aria-live="polite" aria-label="العملية والنتيجة">0</output><div id="message" role="status">اكتب عملية حسابية</div></div>
            <div class="keys" aria-label="أزرار الآلة الحاسبة">
            <button class="clear" data-key="C" aria-label="مسح الكل">C</button><button data-key="Backspace" aria-label="حذف آخر رقم">⌫</button><button class="op" data-key="(" aria-label="فتح قوس">(</button><button class="op" data-key=")" aria-label="إغلاق قوس">)</button>
            <button data-key="7">7</button><button data-key="8">8</button><button data-key="9">9</button><button class="op" data-key="/" aria-label="قسمة">÷</button>
            <button data-key="4">4</button><button data-key="5">5</button><button data-key="6">6</button><button class="op" data-key="*" aria-label="ضرب">×</button>
            <button data-key="1">1</button><button data-key="2">2</button><button data-key="3">3</button><button class="op" data-key="-" aria-label="طرح">−</button>
            <button class="wide" data-key="0">0</button><button data-key=".">.</button><button class="op" data-key="+" aria-label="جمع">+</button>
            <button class="equals" data-key="=" aria-label="يساوي">=</button>
            </div></main>
            <script>
            (()=>{'use strict';
            const display=document.getElementById('display'),message=document.getElementById('message');
            let expression='',finished=false;
            function calculate(source){
              let i=0;
              function finite(n){if(!Number.isFinite(n))throw new Error('قيمة غير صالحة');return n;}
              function primary(){
                if(source[i]==='+'){i++;return primary();}
                if(source[i]==='-'){i++;return -primary();}
                if(source[i]==='('){i++;const n=sum();if(source[i++]!==')')throw new Error('أغلق القوس');return n;}
                const match=source.slice(i).match(/^(?:\\d+\\.?\\d*|\\.\\d+)(?:e[+-]?\\d+)?/i);
                if(!match)throw new Error('أكمل العملية');i+=match[0].length;return finite(Number(match[0]));
              }
              function product(){let n=primary();while(source[i]==='*'||source[i]==='/'){const op=source[i++],right=primary();if(op==='/'&&right===0)throw new Error('لا يمكن القسمة على صفر');n=finite(op==='*'?n*right:n/right);}return n;}
              function sum(){let n=product();while(source[i]==='+'||source[i]==='-'){const op=source[i++],right=product();n=finite(op==='+'?n+right:n-right);}return n;}
              const result=sum();if(i!==source.length)throw new Error('تحقق من العملية');return finite(result);
            }
            function render(){display.textContent=expression.replace(/\\*/g,'×').replace(/\\//g,'÷')||'0';}
            function press(key){
              if(key==='C'){expression='';finished=false;message.textContent='اكتب عملية حسابية';render();return;}
              if(key==='Backspace'){expression=expression.slice(0,-1);finished=false;message.textContent='';render();return;}
              if(key==='='){
                if(!expression)return;
                try{const previous=expression;const n=calculate(expression);expression=Object.is(n,-0)?'0':String(Number(n.toPrecision(12)));finished=true;message.textContent=previous.replace(/\\*/g,'×').replace(/\\//g,'÷')+' =';render();}
                catch(error){display.textContent='خطأ';message.textContent=error.message;expression='';finished=false;}return;
              }
              if(!/^[0-9.+*/()\\-]$/.test(key))return;
              if(finished&&/[0-9.(]/.test(key))expression='';
              finished=false;
              if(expression.length>=240){message.textContent='العملية طويلة؛ احسبها على خطوات';return;}
              if(key==='.'){
                const current=expression.split(/[+*/()\\-]/).pop();if(current.includes('.')||/[eE]/.test(current))return;
                if(!current)expression+='0';
              }
              expression+=key;message.textContent='';render();
            }
            document.querySelector('.keys').addEventListener('click',event=>{const button=event.target.closest('button[data-key]');if(button)press(button.dataset.key);});
            document.addEventListener('keydown',event=>{let key=event.key;if(key==='Enter')key='=';if(key==='Escape'||key==='Delete')key='C';if(/^[0-9.+*/()\\-]$/.test(key)||['=','C','Backspace'].includes(key)){event.preventDefault();press(key);}});
            })();
            </script></body></html>
            """;
    }
}
