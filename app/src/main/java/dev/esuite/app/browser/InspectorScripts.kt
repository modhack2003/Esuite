package dev.esuite.app.browser

/** Page-side instrumentation, without a native bridge or privileged page API. */
object InspectorScripts {
    val select = """
        (() => {
          if (window.__esuitePicker) document.removeEventListener('click', window.__esuitePicker, true);
          window.__esuitePickPending = true;
          window.__esuitePicker = function(event) {
            if (!(event.target instanceof Element)) return;
            event.preventDefault(); event.stopImmediatePropagation();
            window.__esuiteElement = event.target;
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
          if (window.__esuiteHighlight) {
            window.__esuiteHighlight.node.remove();
            window.removeEventListener('scroll', window.__esuiteHighlight.update, true);
            window.removeEventListener('resize', window.__esuiteHighlight.update);
          }
          delete window.__esuiteHighlight; delete window.__esuitePicker; delete window.__esuiteElement;
          delete window.__esuiteDom; delete window.__esuitePickPending;
        })()
    """.trimIndent()
    val pickResult = """
        window.__esuitePickPending === false ? 'Picked' : 'Waiting'
    """.trimIndent()
    val disarmPicker = """
        (() => { document.removeEventListener('click', window.__esuitePicker, true); delete window.__esuitePickPending; })()
    """.trimIndent()
    val highlightSelection = """
        (() => {
          if (!window.__esuiteHighlight) {
            const node = document.createElement('div');
            node.setAttribute('aria-hidden','true');
            node.style.cssText = 'all:initial;position:fixed;z-index:2147483647;pointer-events:none;box-sizing:border-box;border:2px solid #5BE0D6';
            const update = () => {
              const selected = window.__esuiteElement;
              if (!selected || !selected.isConnected) { node.style.display = 'none'; return; }
              const rect = selected.getBoundingClientRect();
              node.style.display = 'block';
              node.style.left = rect.left + 'px'; node.style.top = rect.top + 'px';
              node.style.width = rect.width + 'px'; node.style.height = rect.height + 'px';
            };
            window.__esuiteHighlight = {node, update};
            document.documentElement.appendChild(node);
            window.addEventListener('scroll', update, true);
            window.addEventListener('resize', update);
          }
          window.__esuiteHighlight.update();
        })()
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
          const overlay = window.__esuiteHighlight && window.__esuiteHighlight.node;
          if (overlay && e.contains(overlay)) overlay.remove();
          const html = e.outerHTML;
          if (overlay && !overlay.isConnected) document.documentElement.appendChild(overlay);
          const round = value => Math.round(value * 10) / 10;
          return JSON.stringify({htmlTruncated:html.length>16000,htmlCharacters:html.length,htmlLimit:16000,
            tag:e.tagName,html:html.slice(0,16000),css:(e.getAttribute('style') || '').slice(0,4000),attributes,rules,
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
          if(e.outerHTML !== __ESUITE_EXPECTED__) return 'The page changed this element. Copy your draft and refresh the selection.';
          const range=document.createRange(); range.selectNode(e);
          const fragment=range.createContextualFragment(__ESUITE_VALUE__);
          const replacement=fragment.firstElementChild;
          e.replaceWith(fragment); window.__esuiteElement=replacement;
          return 'HTML updated locally';
        })()
    """.trimIndent()
    val css = """
        (() => {
          const e=window.__esuiteElement;
          if(!e || !e.isConnected) return 'Select an element first';
          e.style.cssText=__ESUITE_VALUE__;
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
          const options = __ESUITE_TREE_OPTIONS__;
          const root = document.documentElement;
          const state = window.__esuiteDom || (window.__esuiteDom = {ids:new WeakMap(), handles:new Map(), next:0});
          const handles = state.handles;
          const id = node => {
            if (!state.ids.has(node)) state.ids.set(node, state.next++);
            return state.ids.get(node);
          };
          const overlay = window.__esuiteHighlight && window.__esuiteHighlight.node;
          const usable = node => node !== overlay && (node.nodeType === 1 || node.nodeType === 8 ||
            (node.nodeType === 3 && node.nodeValue.trim() !== ''));
          const prefix = (text, limit) => {
            const last = text.charCodeAt(limit - 1);
            return text.slice(0, text.length > limit && last >= 0xd800 && last <= 0xdbff ? limit - 1 : limit);
          };
          const voidTags = new Set('area base br col embed hr img input link meta param source track wbr'.split(' '));
          const selected = window.__esuiteElement;
          const path = new Set();
          for (let node = selected; node && node.nodeType === 1; node = node.parentElement) path.add(node);
          const rows = [];
          const include = options.reveal ? new Set(path) : new Set();
          const query = (options.query || '').trim().toLowerCase();
          let limited = false;
          if (query) {
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT | NodeFilter.SHOW_COMMENT);
            let node = root, scanned = 0, matched = 0;
            while (node && scanned < 6000 && matched < 60) {
              scanned++;
              if (usable(node)) {
                const text = node.nodeType === 1
                  ? [node.localName, '#' + prefix(node.id,120),
                      ...prefix(node.getAttribute('class') || '',120).split(/\s+/).map(name => '.' + name),
                      ...Array.from(node.attributes).slice(0,12).map(attr =>
                        attr.name + (hidden(attr.name) ? '' : ' ' + prefix(attr.value,120)))].join(' ')
                  : prefix(node.nodeValue,180);
                if (text.toLowerCase().includes(query)) {
                  matched++;
                  for (let ancestor = node; ancestor && ancestor !== document; ancestor = ancestor.parentNode) include.add(ancestor);
                }
              }
              node = walker.nextNode();
            }
            limited = node !== null;
          }
          function hidden(name) { return /^(on|value|nonce|srcdoc)|secret|token|password/i.test(name); }
          let budget = 100000;
          function visit(node, parent, depth) {
            if (depth > 100 || rows.length >= 400 || budget < 700) { limited = true; return; }
            if (!usable(node) || (query && !include.has(node))) return;
            const key = id(node);
            const tag = node.nodeType === 1 ? prefix(node.localName,80) : '';
            const htmlVoid = node.namespaceURI === 'http://www.w3.org/1999/xhtml' && voidTags.has(tag);
            const attributes = tag ? Array.from(node.attributes).slice(0,10).map(attr => ({
              name:prefix(attr.name,64), value:hidden(attr.name) ? 'Hidden' : prefix(attr.value,100)
            })) : [];
            const opening = tag ? '' : prefix(node.nodeValue,160);
            let hasChildren = false;
            if (tag && !htmlVoid) for (let child = node.firstChild; child; child = child.nextSibling) {
              if (usable(child)) { hasChildren = true; break; }
            }
            const requested = options.expanded[key];
            const capacity = typeof requested === 'number' ? requested : (node === root ? 24 : 0);
            const expanded = hasChildren && (query !== '' || capacity > 0 || (options.reveal && path.has(node)));
            const row = {id:key, parent, depth, tag, attributes, text:opening, comment:node.nodeType===8,
              children:hasChildren, expanded, voidTag:htmlVoid, more:false,
              attributesLimited:tag ? node.attributes.length > attributes.length : false};
            budget -= JSON.stringify(row).length;
            handles.delete(key);
            handles.set(key,node);
            if (handles.size > 800) handles.delete(handles.keys().next().value);
            rows.push(row);
            if (!expanded) return;
            let count = 0;
            const batch = Math.max(24, Math.min(capacity,600));
            for (let child = node.firstChild; child; child = child.nextSibling) {
              if (!usable(child) || (query && !include.has(child))) continue;
              if (rows.length >= 400 || budget < 700) { row.more = !query; limited = true; break; }
              if (query || count < batch || include.has(child)) visit(child,key,depth + 1);
              else row.more = true;
              count++;
            }
          }
          visit(root,-1,0);
          return JSON.stringify({rows,limited,selected:selected && selected.isConnected ? id(selected) : null});
        })()
    """.trimIndent()

    val selectTreeElement = """
        (() => {
          const element = window.__esuiteDom && window.__esuiteDom.handles.get(__ESUITE_NODE__);
          if (!element || !element.isConnected || !(element instanceof Element)) return 'The element changed. Refresh the tree.';
          document.removeEventListener('click', window.__esuitePicker, true);
          window.__esuiteElement = element;
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
          let state = window.__esuiteTimings;
          if (!state) {
            state = window.__esuiteTimings = {ids:new WeakMap(), next:0, recent:[]};
            if (typeof PerformanceObserver === 'function') {
              try {
                const observer = new PerformanceObserver(list => {
                  state.recent = state.recent.concat(list.getEntries()).slice(-140);
                });
                observer.observe({entryTypes:['resource']});
                state.observer = observer;
              } catch (_) { }
            }
          }
          const resources = Array.from(new Set(performance.getEntriesByType('resource').slice(-140).concat(state.recent)));
          resources.sort((first,second) => first.startTime - second.startTime);
          const entries = performance.getEntriesByType('navigation').concat(resources.slice(-140));
          const milliseconds = value => Number.isFinite(value) && value >= 0 ? Math.round(value * 10) / 10 : null;
          const phase = (start,end) => start > 0 && end >= start ? milliseconds(end-start) : null;
          const size = value => Number.isSafeInteger(value) && value >= 0 ? value : null;
          function type(item,url) {
            if (item.entryType === 'navigation' || item.initiatorType === 'iframe') return 'document';
            if (item.initiatorType === 'fetch' || item.initiatorType === 'xmlhttprequest') return 'fetch';
            if (item.initiatorType === 'script') return 'script';
            if (item.initiatorType === 'img' || item.initiatorType === 'image') return 'image';
            if (/\.css$/i.test(url.pathname)) return 'stylesheet';
            if (/\.(woff2?|ttf|otf)$/i.test(url.pathname)) return 'font';
            if (['audio','video','track'].includes(item.initiatorType)) return 'media';
            return 'other';
          }
          for (const item of entries) {
            let url;
            try { url = new URL(item.name, document.baseURI); } catch (_) { continue; }
            if (url.protocol !== 'https:' || url.origin !== location.origin) continue;
            if (!state.ids.has(item)) state.ids.set(item,state.next++);
            rows.push({id:state.ids.get(item), path:url.pathname.slice(0,280), pathClipped:url.pathname.length > 280, kind:type(item,url),
              status:item.responseStatus || null, start:milliseconds(item.startTime), duration:milliseconds(item.duration),
              transfer:size(item.transferSize), protocol:(item.nextHopProtocol || '').slice(0,24),
              dns:phase(item.domainLookupStart,item.domainLookupEnd), connect:phase(item.connectStart,item.connectEnd),
              tls:phase(item.secureConnectionStart,item.connectEnd), wait:phase(item.requestStart,item.responseStart),
              download:phase(item.responseStart,item.responseEnd), encoded:size(item.encodedBodySize), decoded:size(item.decodedBodySize)});
          }
          return JSON.stringify({timeOrigin:performance.timeOrigin || performance.timing.navigationStart, rows});
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
