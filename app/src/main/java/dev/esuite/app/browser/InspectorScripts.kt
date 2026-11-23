package dev.esuite.app.browser

/** Page-side instrumentation, without a native bridge or privileged page API. */
object InspectorScripts {
    val select = """
        (() => {
          if (window.__esuitePicker) document.removeEventListener('click', window.__esuitePicker, true);
          window.__esuitePickPending = true;
          window.__esuitePicker = function(event) {
            if (!(event.target instanceof Element) || !event.target.style) return;
            event.preventDefault(); event.stopImmediatePropagation();
            if(window.__esuiteElement) window.__esuiteElement.style.outline = window.__esuiteOutline || '';
            window.__esuiteElement = event.target; window.__esuiteOutline = event.target.style.outline;
            event.target.style.outline = '2px solid #5BE0D6';
            document.removeEventListener('click', window.__esuitePicker, true);
            window.__esuitePickPending = false;
          };
          document.addEventListener('click', window.__esuitePicker, true);
          return 'Tap an element on the page to inspect it.';
        })()
    """.trimIndent()
    val clearSelection = """
        (() => {
          document.removeEventListener('click', window.__esuitePicker, true);
          if (window.__esuiteElement && window.__esuiteElement.isConnected) {
            window.__esuiteElement.style.outline = window.__esuiteOutline || '';
          }
          delete window.__esuitePicker; delete window.__esuiteElement; delete window.__esuiteOutline;
          delete window.__esuiteTree; delete window.__esuitePickPending;
        })()
    """.trimIndent()
    val pickResult = """
        window.__esuitePickPending === false ? 'Picked' : 'Waiting'
    """.trimIndent()
    val disarmPicker = """
        (() => { document.removeEventListener('click', window.__esuitePicker, true); delete window.__esuitePickPending; })()
    """.trimIndent()
    val selection = """
        (() => {
          const e = window.__esuiteElement;
          if (!e || !e.isConnected) return 'null';
          const style = getComputedStyle(e);
          const rect = e.getBoundingClientRect();
          const computed = names => names.map(name => ({name, value: style.getPropertyValue(name).trim().slice(0, 180)}));
          const attributes = Array.from(e.attributes).slice(0, 25).map(attr => ({
            name: attr.name.slice(0, 80),
            value: /value|nonce|secret|token|password|srcdoc/i.test(attr.name) ? 'Hidden' : attr.value.slice(0, 160)
          }));
          const rules = [];
          let budget = 300;
          const visit = list => {
            for (let index = 0; index < list.length && budget > 0 && rules.length < 12; index++) {
              budget--;
              const rule = list[index];
              if (rule.type === 1 && rule.selectorText) {
                try {
                  if (e.matches(rule.selectorText)) rules.push({name:rule.selectorText.slice(0, 150), value:rule.style.cssText.slice(0, 360)});
                } catch (_) { }
              } else if (rule.type === 4 && matchMedia(rule.conditionText).matches) visit(rule.cssRules);
              else if (rule.type === 12 && CSS.supports(rule.conditionText)) visit(rule.cssRules);
            }
          };
          for (const sheet of Array.from(document.styleSheets).slice(0, 30)) {
            if (budget <= 0 || rules.length >= 12) break;
            try { if (!sheet.disabled && (!sheet.media.mediaText || matchMedia(sheet.media.mediaText).matches)) visit(sheet.cssRules); } catch (_) { }
          }
          const html = e.outerHTML;
          const inline = document.createElement('span').style;
          inline.cssText = e.style.cssText;
          inline.outline = window.__esuiteOutline || '';
          const round = value => Math.round(value * 10) / 10;
          return JSON.stringify({htmlTruncated:html.length>16000,htmlCharacters:html.length,htmlLimit:16000,
            tag:e.tagName,html:html.slice(0,16000),css:inline.cssText.slice(0,4000),attributes,rules,
            computed:computed(['display','position','color','background-color','font-family','font-size','font-weight',
              'line-height','opacity','visibility','z-index','overflow','box-sizing','justify-content','align-items','gap']),
            layout:computed(['margin','border-width','padding','width','height','min-width','max-width','min-height','max-height']),
            bounds:[round(rect.x), round(rect.y), round(rect.width), round(rect.height)],
            scroll:[e.scrollWidth,e.scrollHeight,e.clientWidth,e.clientHeight]});
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
    val elementTree = """
        (() => {
          const walker = document.createTreeWalker(document.documentElement, NodeFilter.SHOW_ELEMENT);
          const nodes = [], rows = [], parents = new Map();
          let element = walker.currentNode;
          while (element && rows.length < 160) {
            const parent = parents.has(element.parentElement) ? parents.get(element.parentElement) : -1;
            const depth = parent < 0 ? 0 : rows[parent].depth + 1;
            const classes = (element.getAttribute('class') || '').trim().split(/\s+/).slice(0, 3).map(name => name.slice(0, 36));
            rows.push({id:rows.length, parent, depth, tag:element.tagName.slice(0,50),
              name:element.id.slice(0,64), classes:classes.filter(Boolean), children:element.childElementCount});
            parents.set(element, nodes.length);
            nodes.push(element);
            element = walker.nextNode();
          }
          window.__esuiteTree = nodes;
          return JSON.stringify({rows, limited:element !== null});
        })()
    """.trimIndent()

    val selectTreeElement = """
        (() => {
          const element = window.__esuiteTree && window.__esuiteTree[__ESUITE_NODE__];
          if (!element || !element.isConnected || !element.style) return 'The element changed. Refresh the tree.';
          document.removeEventListener('click', window.__esuitePicker, true);
          if (window.__esuiteElement) window.__esuiteElement.style.outline = window.__esuiteOutline || '';
          window.__esuiteElement = element;
          window.__esuiteOutline = element.style.outline;
          element.style.outline = '2px solid #5BE0D6';
          return 'Selected';
        })()
    """.trimIndent()

    val scrollToElement = """
        (() => {
          const element = window.__esuiteElement;
          if (!element || !element.isConnected) return 'Select an element first.';
          element.scrollIntoView({block:'center',inline:'nearest'});
          return 'Element shown on page';
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
          const entries = performance.getEntriesByType('navigation').concat(performance.getEntriesByType('resource').slice(-100));
          const milliseconds = value => Number.isFinite(value) && value >= 0 ? Math.round(value * 10) / 10 : null;
          const phase = (start,end) => start > 0 && end >= start ? milliseconds(end-start) : null;
          for (const item of entries) {
            const url = new URL(item.name, document.baseURI);
            if (url.origin !== location.origin) continue;
            rows.push({path:url.pathname.slice(0,280), kind:item.entryType==='navigation' ? 'document' : (item.initiatorType || 'other'),
              status:item.responseStatus || null, start:milliseconds(item.startTime), duration:milliseconds(item.duration),
              transfer:Number.isFinite(item.transferSize) ? item.transferSize : null, protocol:(item.nextHopProtocol || '').slice(0,24),
              dns:phase(item.domainLookupStart,item.domainLookupEnd), connect:phase(item.connectStart,item.connectEnd),
              wait:phase(item.requestStart,item.responseStart), download:phase(item.responseStart,item.responseEnd)});
          }
          return JSON.stringify(rows);
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
