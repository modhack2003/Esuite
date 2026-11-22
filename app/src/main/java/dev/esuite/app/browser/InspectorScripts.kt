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
        (() => { const e=window.__esuiteElement; if(!e || !e.isConnected) return 'No selected element'; const s=getComputedStyle(e);
          return JSON.stringify({htmlTruncated:e.outerHTML.length>16000,htmlCharacters:e.outerHTML.length,htmlLimit:16000,
            tag:e.tagName,html:e.outerHTML.slice(0,16000),css:e.style.cssText,
            computed:{display:s.display,color:s.color,background:s.backgroundColor,font:s.font,position:s.position}},null,2);
        })()
    """.trimIndent()
    val html = """
        (() => {
          const e=window.__esuiteElement;
          if(!e || !e.isConnected) return 'Select an element first';
          if(!e.parentNode || e===document.documentElement) return 'Select a child element to edit';
          const range=document.createRange(); range.selectNode(e);
          const fragment=range.createContextualFragment(__ESUITE_VALUE__);
          const replacement=fragment.firstElementChild;
          e.replaceWith(fragment); window.__esuiteElement=replacement;
          window.__esuiteOutline=replacement?.style.outline || '';
          if(replacement) replacement.style.outline='2px solid #5BE0D6';
          return 'HTML updated locally';
        })()
    """.trimIndent()
    val css = """
        (() => {
          const e=window.__esuiteElement;
          if(!e || !e.isConnected) return 'Select an element first';
          e.style.cssText=__ESUITE_VALUE__;
          window.__esuiteOutline=e.style.outline;
          e.style.outline='2px solid #5BE0D6';
          return 'CSS updated locally';
        })()
    """.trimIndent()
    val storage = """
        (() => {
          const prefix = (text, limit) => {
            const last = text.charCodeAt(limit - 1);
            return text.slice(0, text.length > limit && last >= 0xd800 && last <= 0xdbff ? limit - 1 : limit);
          };
          const read = (storage) => {
            const total = storage.length;
            const entries = [];
            let remaining = 9500;
            for (let index = 0; index < Math.min(total, 100); index++) {
              const key = storage.key(index);
              if (key === null) break;
              const value = storage.getItem(key);
              if (value === null) continue;
              const name = prefix(key, 384);
              let preview = prefix(value, 4096);
              const entry = () => ({key:name, keyCharacters:key.length, keyTruncated:name.length < key.length,
                value:preview, valueCharacters:value.length, valueTruncated:preview.length < value.length});
              let cost = JSON.stringify(entry(), null, 2).length + 100;
              if (cost > remaining) {
                preview = prefix(value, Math.max(0, Math.floor((remaining - JSON.stringify({...entry(), value:''}, null, 2).length - 100) / 6)));
                cost = JSON.stringify(entry(), null, 2).length + 100;
              }
              if (cost > remaining) break;
              entries.push(entry());
              remaining -= cost;
            }
            return {totalKeys:total, returnedKeys:entries.length, omittedKeys:total - entries.length, entries};
          };
          try {
            return JSON.stringify({localStorage:read(localStorage),sessionStorage:read(sessionStorage)},null,2);
          } catch (e) { return String(e); }
        })()
    """.trimIndent()
    val documentOutline = """
        (() => {
          const rows = Array.from(document.querySelectorAll('html, body, main, nav, article, section, h1, h2, h3, a, button, input')).slice(0, 60);
          return rows.map(element => element.tagName + (element.id ? '#' + element.id.slice(0, 80) : '')).join('\n') || 'No matching elements';
        })()
    """.trimIndent()

    val storageMetadata = """
        (() => {
          const keys = storage => {
            const records = [];
            for (let index = 0; index < Math.min(storage.length, 50); index++) {
              const key = storage.key(index);
              if (key !== null) records.push(key.slice(0, 80) + ' · ' + (storage.getItem(key) || '').length + ' characters');
            }
            return [storage.length + ' keys', ...records].join('\n');
          };
          return 'Local storage · ' + keys(localStorage) + '\n\nSession storage · ' + keys(sessionStorage);
        })()
    """.trimIndent()
}
