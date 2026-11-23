package dev.esuite.app.browser

/** Page-side instrumentation, without a native bridge or privileged page API. */
object InspectorScripts {
    val select = """
        (() => {
          if (window.__esuitePicker) document.removeEventListener('click', window.__esuitePicker, true);
          window.__esuitePicker = function(event) {
            if (!(event.target instanceof Element) || !event.target.style) return;
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
    val clearSelection = """
        (() => {
          document.removeEventListener('click', window.__esuitePicker, true);
          if (window.__esuiteElement && window.__esuiteElement.isConnected) {
            window.__esuiteElement.style.outline = window.__esuiteOutline || '';
          }
          delete window.__esuitePicker; delete window.__esuiteElement; delete window.__esuiteOutline;
        })()
    """.trimIndent()
    val selection = """
        (() => { const e=window.__esuiteElement; if(!e || !e.isConnected) return 'No selected element'; const s=getComputedStyle(e);
          return JSON.stringify({htmlTruncated:e.outerHTML.length>16000,htmlCharacters:e.outerHTML.length,htmlLimit:16000,
            tag:e.tagName,html:e.outerHTML.slice(0,16000),css:e.style.cssText.slice(0,4000),
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
          window.__esuiteOutline=replacement ? replacement.style.outline : '';
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

    val networkTiming = """
        (() => {
          const rows = [];
          for (const item of performance.getEntriesByType('resource').slice(-120).reverse()) {
            const url = new URL(item.name, document.baseURI);
            if (url.origin !== location.origin) continue;
            const status = item.responseStatus ? ' · HTTP ' + item.responseStatus : '';
            const size = item.transferSize ? ' · ' + item.transferSize + ' B transferred' : '';
            rows.push(item.initiatorType + ' ' + url.pathname.slice(0, 180) + ' · ' + Math.round(item.duration) + ' ms' + status + size);
            if (rows.length === 30) break;
          }
          return rows.join('\n');
        })()
    """.trimIndent()

    val sourceFiles = """
        (() => {
          const safePath = value => {
            const url = new URL(value, document.baseURI);
            return url.protocol === 'https:' ? (url.origin === location.origin ? '' : url.origin) + url.pathname.slice(0, 160) : url.protocol;
          };
          const groups = [
            ['Document', [location.href]],
            ['Stylesheets', Array.from(document.querySelectorAll('link[rel="stylesheet"][href]')).slice(0, 24).map(node => node.href)],
            ['Scripts', Array.from(document.querySelectorAll('script[src]')).slice(0, 30).map(node => node.src)]
          ];
          return groups.map(([title, urls]) => title + '\n' + (urls.map(safePath).join('\n') || 'None found')).join('\n\n');
        })()
    """.trimIndent()

    val performanceSummary = """
        (() => {
          const entry = performance.getEntriesByType('navigation')[0];
          const rows = [];
          const timing = (label, value) => { if (value > 0) rows.push(label + ' · ' + Math.round(value) + ' ms'); };
          if (entry) {
            timing('Time to first byte', entry.responseStart);
            timing('DOM interactive', entry.domInteractive);
            timing('DOMContentLoaded', entry.domContentLoadedEventEnd);
            timing('Load event', entry.loadEventEnd);
          }
          for (const paint of performance.getEntriesByType('paint')) timing(paint.name, paint.startTime);
          rows.push('Timed resources · ' + performance.getEntriesByType('resource').length);
          return rows.join('\n');
        })()
    """.trimIndent()

    val viewportSummary = """
        (() => {
          const viewport = window.visualViewport;
          const declaration = document.querySelector('meta[name="viewport"]');
          let dynamicHeight = 'Unsupported';
          if (CSS.supports('height', '100dvh')) {
            const measure = document.createElement('div');
            measure.style.cssText = 'position:fixed;visibility:hidden;pointer-events:none;height:100dvh';
            document.documentElement.appendChild(measure);
            dynamicHeight = Math.round(measure.getBoundingClientRect().height) + ' CSS px';
            measure.remove();
          }
          return [
            'Layout viewport · ' + innerWidth + ' × ' + innerHeight + ' CSS px',
            viewport ? 'Visual viewport · ' + Math.round(viewport.width) + ' × ' + Math.round(viewport.height) + ' CSS px' : 'Visual viewport unavailable',
            'Device pixel ratio · ' + devicePixelRatio,
            'Page width · ' + document.documentElement.scrollWidth + ' CSS px',
            'Dynamic viewport height · ' + dynamicHeight,
            'Viewport declaration · ' + (declaration ? declaration.content.slice(0, 240) : 'None'),
            'Document language · ' + (document.documentElement.lang.slice(0, 24) || 'Unspecified')
          ].join('\n');
        })()
    """.trimIndent()

    val fitClippedDialogs = """
        (() => {
          const visualViewport = window.visualViewport;
          const pageHeight = () => visualViewport && visualViewport.height > 0 ? visualViewport.height : innerHeight;
          const viewport = pageHeight();
          if (viewport < 160) return 0;
          let fitted = 0;
          for (const dialog of Array.from(document.querySelectorAll('dialog[open],[role="dialog"]')).slice(0, 12)) {
            const declared = /^([0-9.]+)dvh$/i.exec(dialog.style.maxHeight) ||
              Array.from(dialog.classList).map(name => /^max-h-\[([0-9.]+)dvh\]$/.exec(name)).find(Boolean);
            if (!declared) continue;
            const height = Number(declared[1]);
            if (height < 40 || height > 100) continue;
            const css = getComputedStyle(dialog);
            const limit = parseFloat(css.maxHeight);
            const intended = viewport * height / 100;
            const bounds = dialog.getBoundingClientRect();
            if (css.position !== 'fixed' || css.overflowY !== 'auto' || bounds.height <= 0 ||
                !Number.isFinite(limit) || limit > intended * 0.35 || bounds.height > intended * 0.45 ||
                dialog.scrollHeight - dialog.clientHeight < 64) continue;
            const updateLimit = () => {
              if (dialog.isConnected) {
                dialog.style.maxHeight = Math.floor(pageHeight() * height / 100) + 'px';
              } else {
                window.removeEventListener('resize', updateLimit);
                if (visualViewport) visualViewport.removeEventListener('resize', updateLimit);
              }
            };
            updateLimit();
            window.addEventListener('resize', updateLimit, {passive:true});
            if (visualViewport) visualViewport.addEventListener('resize', updateLimit, {passive:true});
            fitted++;
          }
          return fitted;
        })()
    """.trimIndent()

    val securitySummary = """
        (() => [
          'Origin · ' + location.origin,
          'Secure context · ' + (window.isSecureContext ? 'Yes' : 'No'),
          'Cross-origin isolation · ' + (window.crossOriginIsolated ? 'Yes' : 'No'),
          'Certificate validation uses Android system trust.',
          'Insecure mixed content is blocked.'
        ].join('\n'))()
    """.trimIndent()

    val accessibilitySummary = """
        (() => {
          const headings = Array.from(document.querySelectorAll('h1,h2,h3,h4,h5,h6,[role="heading"]')).slice(0, 30);
          const landmarks = Array.from(document.querySelectorAll('main,nav,header,footer,aside,[role="main"],[role="navigation"]')).slice(0, 20);
          return [
            'Language · ' + (document.documentElement.lang || 'Unspecified').slice(0, 24),
            'Headings · ' + headings.length + ' shown',
            ...headings.map(item => item.tagName + ' · ' + item.textContent.trim().replace(/\s+/g, ' ').slice(0, 100)),
            'Landmarks · ' + landmarks.length + ' shown',
            ...landmarks.map(item => item.getAttribute('role') || item.tagName.toLowerCase())
          ].join('\n');
        })()
    """.trimIndent()
}
