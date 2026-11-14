package dev.esuite.app.browser

/** Page-side instrumentation, without a native bridge or privileged page API. */
object InspectorScripts {
    val select = """
        (() => {
          if (window.__esuitePicker) document.removeEventListener('click', window.__esuitePicker, true);
          window.__esuitePicker = function(event) {
            event.preventDefault(); event.stopImmediatePropagation();
            if(window.__esuiteElement) window.__esuiteElement.style.outline = window.__esuiteOutline || '';
            window.__esuiteElement = event.target; window.__esuiteOutline = event.target.style.outline;
            event.target.style.outline = '2px solid #5BE0D6';
            document.removeEventListener('click', window.__esuitePicker, true);
          };
          document.addEventListener('click', window.__esuitePicker, true);
          return 'Tap an element, then press Read selection. The selection click is consumed locally.';
        })()
    """.trimIndent()
    val selection = """
        (() => { const e=window.__esuiteElement; if(!e) return 'No selected element'; const s=getComputedStyle(e);
          return JSON.stringify({tag:e.tagName,html:e.outerHTML.slice(0,16000),css:e.style.cssText,
            computed:{display:s.display,color:s.color,background:s.backgroundColor,font:s.font,position:s.position}},null,2);
        })()
    """.trimIndent()
    val storage = """
        (() => { try { const read=s=>Object.fromEntries(Object.keys(s).slice(0,100).map(k=>[k,s.getItem(k)?.slice(0,4096)]));
          return JSON.stringify({localStorage:read(localStorage),sessionStorage:read(sessionStorage)},null,2);
        } catch(e) { return String(e); } })()
    """.trimIndent()
}
