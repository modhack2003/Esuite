import { useEffect, useRef, useState } from 'react';
import './ESuiteMobileWorkbench.css';

type Page = 'Workspace' | 'Browser' | 'Traffic' | 'API Lab' | 'Findings' | 'Tools';
type Glyph = 'grid' | 'globe' | 'traffic' | 'code' | 'flag' | 'arrow' | 'shield' | 'play' | 'search' | 'close' | 'sliders';
const paths: Record<Glyph,string> = {
  grid:'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  globe:'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M3 12h18 M12 3c-5 5-5 13 0 18 5-5 5-13 0-18',
  traffic:'M4 7h15m-4-4 4 4-4 4 M20 17H5m4-4-4 4 4 4',
  code:'m8 6-6 6 6 6 m8-12 6 6-6 6 m-3-15-2 18',
  flag:'M5 21V3h14l-3 5 3 5H5', arrow:'M5 12h14m-5-5 5 5-5 5',
  shield:'m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3m-4 9 3 3 5-6',
  play:'m8 4 12 8-12 8z', search:'M16 16l5 5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  close:'m5 5 14 14 M19 5 5 19', sliders:'M4 7h16 M4 17h16 M8 4v6 M16 14v6'
};
function Icon({name,size=18}:{name:Glyph;size?:number}) { return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]}/></svg>; }
function Mark(){return <svg viewBox="0 0 40 40" width="34" height="34" fill="none" aria-hidden="true"><path d="M8 8h25l-5 6H14v4h15l-5 6H14v4h19l-5 6H8V8Z" fill="currentColor"/></svg>;}
const requests = [
 {method:'GET',path:'/api/profile',status:200,type:'application/json',time:'84 ms',body:'{\n  "id": "user_a",\n  "name": "Demo User A",\n  "role": "member"\n}'},
 {method:'POST',path:'/api/session',status:201,type:'application/json',time:'126 ms',body:'{\n  "session": "synthetic-session",\n  "expires_in": 3600\n}'},
 {method:'GET',path:'/api/projects',status:200,type:'application/json',time:'63 ms',body:'{\n  "projects": ["Sandbox API"]\n}'},
 {method:'GET',path:'/api/admin',status:403,type:'application/json',time:'42 ms',body:'{\n  "error": "Access denied"\n}'},
 {method:'GET',path:'/assets/app.js',status:200,type:'text/javascript',time:'18 ms',body:'// Synthetic asset fixture'}
];
const nav: [Page,Glyph][] = [['Workspace','grid'],['Browser','globe'],['Traffic','traffic'],['API Lab','code'],['Findings','flag']];

export const ESuiteMobileWorkbench = () => {
 const [page,setPage] = useState<Page>('Workspace');
 const [query,setQuery] = useState('');
 const [capture,setCapture] = useState(false);
 const [selected,setSelected] = useState<number|null>(null);
 const [browserTab,setBrowserTab] = useState('Elements');
 const [identity,setIdentity] = useState('Account A');
 const [editor,setEditor] = useState('GET /api/profile HTTP/1.1\nHost: api.esuite.test\nAccept: application/json');
 const [response,setResponse] = useState('');
 const [compare,setCompare] = useState(false);
 const [codecInput,setCodecInput] = useState('ESuite: inspect anywhere.');
 const [codecOutput,setCodecOutput] = useState('');
 const [note,setNote] = useState('');
 const [notes,setNotes] = useState<string[]>([]);
 const [toast,setToast] = useState('');
 const drawerRef = useRef<HTMLDivElement>(null);
 useEffect(()=>{
   if(selected===null) return;
   const previous = document.activeElement as HTMLElement|null;
   const drawer = drawerRef.current;
   drawer?.querySelector<HTMLButtonElement>('button')?.focus();
   const handleKey=(event:KeyboardEvent)=>{
     if(event.key==='Escape'){event.preventDefault();setSelected(null);return;}
     if(event.key!=='Tab'||!drawer)return;
     const controls=Array.from(drawer.querySelectorAll<HTMLButtonElement>('button'));
     const first=controls[0],last=controls[controls.length-1];
     if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
     else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
   };
   document.addEventListener('keydown',handleKey);
   return ()=>{document.removeEventListener('keydown',handleKey);if(previous?.isConnected)previous.focus();};
 },[selected]);
 const go=(next:Page)=>{setPage(next);setSelected(null);setToast('');};
 const notify=(message:string)=>setToast(message);
 const sendToEditor=(i:number)=>{setEditor(`${requests[i].method} ${requests[i].path} HTTP/1.1\nHost: api.esuite.test\nAccept: ${requests[i].type}`);setResponse('');go('API Lab');};
 const codec=(decode:boolean)=>{try {setCodecOutput(decode ? new TextDecoder('utf-8',{fatal:true}).decode(Uint8Array.from(atob(codecInput),c=>c.charCodeAt(0))) : btoa(Array.from(new TextEncoder().encode(codecInput),b=>String.fromCharCode(b)).join('')));} catch {setCodecOutput('Invalid Base64 or UTF-8 input.');}};
 const title = page==='Workspace' ? 'Your workspace.' : page==='API Lab' ? 'Request lab.' : page==='Browser' ? 'Look closer.' : page==='Traffic' ? 'Every request.' : page==='Tools' ? 'Small tools. Big clarity.' : 'Keep the evidence.';
 return <div className="es-design">
   <header className="es-brandbar"><a className="es-brand" href="#"><Mark/><span>ESuite<span className="es-brand-dot">.</span></span></a><span className="es-top-label">ANDROID SECURITY WORKBENCH</span><span className="es-version">DESIGN PREVIEW <b>01</b></span></header>
   <main className="es-board">
    <aside className="es-intro">
     <div className="es-eyebrow"><span className="es-line"/> BUILT FOR THE WAY YOU TEST</div>
     <h1>Your pocket.<br/><span>Full visibility.</span></h1>
     <p className="es-lead">Inspect a page. Follow the request.<br/>Understand what happens next.</p>
     <div className="es-pillrow"><span>No root</span><span>No computer*</span><span>On-device</span></div>
     <div className="es-preview-nav" aria-label="Preview screens">{[...nav,['Tools','sliders'] as [Page,Glyph]].map(([p,icon])=><button key={p} onClick={()=>go(p)} aria-pressed={page===p} className={page===p?'active':''}><Icon name={icon}/>{p}<span>↗</span></button>)}</div>
     <p className="es-footnote">*During use. Development requires an Android build environment. This interactive concept uses synthetic data. It makes no network requests.</p>
     <div className="es-edition"><span className="es-monogram">E / 01</span><span>INSPECT. INTERCEPT.<br/>INVESTIGATE.</span></div>
    </aside>
    <section className="es-device" aria-label="ESuite Android interface concept">
     <div className="es-statusbar"><span>9:41</span><span className="es-camera"/><span>▰ ▰ 87%</span></div>
     <header className="es-appbar"><div className="es-app-brand"><Mark/><strong>ESuite</strong><span>ALPHA UI</span></div><button className="es-iconbtn" aria-label="Open project information" onClick={()=>notify('Sandbox API · synthetic project · data resets on reload.')}><Icon name="sliders"/></button></header>
     <div className="es-demo"><span className="es-statusdot"/> SYNTHETIC DEMO <span>LOCAL PREVIEW</span></div>
     <div className="es-screen">
      <div className="es-screen-heading"><div className="es-eyebrow">SANDBOX API <span>/ {page.toUpperCase()}</span></div><h2>{title}</h2></div>
      {page==='Workspace' && <>
       <div className="es-project-card"><div className="es-project-top"><span className="es-project-icon"><Icon name="shield" size={23}/></span><span className="es-badge">DEMO PROJECT</span></div><h3>Sandbox API</h3><p>api.esuite.test</p><div className="es-project-footer"><span><i/> On-device workspace</span><button onClick={()=>go('Browser')} aria-label="Open demo browser"><Icon name="arrow"/></button></div></div>
       <div className="es-statrow"><div><span>REQUESTS</span><strong>05</strong><small>Fixture records</small></div><div><span>IDENTITIES</span><strong>02</strong><small>Account A + B</small></div><div><span>NOTES</span><strong>{String(notes.length).padStart(2,'0')}</strong><small>This session</small></div></div>
       <div className="es-section-title"><h3>Start investigating</h3><span>YOUR TOOLKIT</span></div>
       <div className="es-toolgrid">{[['Browser','globe','Inspect elements'],['Traffic','traffic','Follow the exchange'],['API Lab','code','Edit. Replay. Compare.'],['Tools','sliders','Decode & understand']] .map(([p,icon,desc])=><button key={p} className="es-toolcard" onClick={()=>go(p as Page)}><Icon name={icon as Glyph}/><strong>{p}</strong><small>{desc}</small><span>↗</span></button>)}</div>
       <div className="es-infobox"><Icon name="shield"/><p><b>Your work stays with you.</b><br/>Local processing is the product requirement. Capture engines are still planned.</p></div>
      </>}
      {page==='Traffic' && <>
       <div className="es-capture-card"><div><span className={capture?'es-statusdot':'es-offdot'}/><strong>{capture?'Demo capture running':'Demo capture paused'}</strong><small>Selected app · Sandbox Browser</small></div><button className={capture?'es-button es-secondary':'es-button'} onClick={()=>setCapture(!capture)}>{capture?'Pause':'Start demo'}</button></div>
       <label className="es-search"><Icon name="search"/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Filter by path or method" aria-label="Filter traffic"/><kbd>/</kbd></label>
       <div className="es-section-title"><h3>HTTP history</h3><span>{requests.filter(r=>(r.path+r.method).toLowerCase().includes(query.toLowerCase())).length} FIXTURES</span></div>
       <div className="es-requestlist">{requests.map((r,i)=>({r,i})).filter(({r})=>(r.path+r.method).toLowerCase().includes(query.toLowerCase())).map(({r,i})=><button key={r.path} onClick={()=>setSelected(i)} className="es-request"><div><span className={'es-method '+(r.method==='POST'?'post':'')}>{r.method}</span><strong>{r.path}</strong><span className={r.status===403?'es-http amber':'es-http'}>{r.status}</span></div><p>api.esuite.test <span>{r.time} · {r.type==='application/json'?'JSON':'JS'}</span></p></button>)}</div>
       {!requests.some(r=>(r.path+r.method).toLowerCase().includes(query.toLowerCase()))&&<div className="es-empty">No fixture requests match this filter.</div>}
       <p className="es-micro">Tap a request to inspect it. These are bundled fixtures, not captured traffic.</p>
      </>}
      {page==='Browser' && <>
       <div className="es-address"><Icon name="shield" size={15}/><span>https://sandbox.esuite.test</span><span className="es-badge">FIXTURE</span></div>
       <div className="es-website"><span>SANDBOX / DEMO</span><h3>Hello, investigator.</h3><p>A tiny page. A closer look.</p><button onClick={()=>notify('Selected <button class="profile-action">. DOM selection is simulated.')} className="es-fake-webbutton">View profile <span>↗</span></button><div className="es-selection-tag">button.profile-action · 302 × 44</div></div>
       <div className="es-tabrow">{['Elements','Cookies','Storage','Console'].map(t=><button key={t} onClick={()=>setBrowserTab(t)} aria-pressed={browserTab===t} className={browserTab===t?'active':''}>{t}</button>)}</div>
       {browserTab==='Elements'&&<pre className="es-code"><span className="es-muted">01 </span>&lt;main class="sandbox"&gt;{'\n'}<span className="es-muted">02 </span>  &lt;h1&gt;Hello, investigator.&lt;/h1&gt;{'\n'}<span className="es-selected-code">03   &lt;button class="profile-action"&gt;</span>{'\n'}<span className="es-muted">04 </span>    View profile{'\n'}<span className="es-muted">05 </span>  &lt;/button&gt;{'\n'}<span className="es-muted">06 </span>&lt;/main&gt;</pre>}
       {browserTab==='Cookies'&&<div className="es-cookie"><div><b>session_demo</b><span className="es-badge">HttpOnly</span></div><code>synthetic-session-value</code><dl><dt>Domain</dt><dd>sandbox.esuite.test</dd><dt>Path</dt><dd>/</dd><dt>Secure</dt><dd>Yes</dd><dt>SameSite</dt><dd>Lax</dd></dl><p className="es-micro">Fixture metadata. Native cookie inspection is planned.</p></div>}
       {browserTab==='Storage'&&<div className="es-cookie"><b>localStorage</b><pre className="es-code">theme: "midnight"{'\n'}workspace: "sandbox"</pre><p className="es-micro">Synthetic storage records.</p></div>}
       {browserTab==='Console'&&<div className="es-cookie"><pre className="es-code">› document.title{'\n'}"ESuite Sandbox"{'\n'}{'\n'}› location.hostname{'\n'}"sandbox.esuite.test"</pre><p className="es-micro">Example console transcript. No live JS execution.</p></div>}
      </>}
      {page==='API Lab' && <>
       <div className="es-identity"><span>TEST IDENTITY</span><select aria-label="Test identity" value={identity} onChange={e=>{setIdentity(e.target.value);setResponse('');}}><option>Account A</option><option>Account B</option></select><span className="es-badge">FIXTURE</span></div>
       <label className="es-editor-label">REQUEST <span>EDITABLE · NO NETWORK</span><textarea spellCheck={false} aria-label="Request editor" value={editor} onChange={e=>{setEditor(e.target.value);setResponse('');}}/></label>
       <div className="es-actionrow"><button className="es-button" onClick={()=>{setResponse(identity==='Account A'?requests[0].body:'{\n  "id": "user_b",\n  "name": "Demo User B",\n  "role": "member"\n}');notify('Fixture response loaded. The edited request was not sent.');}}><Icon name="play" size={14}/>Simulate send</button><button className="es-button es-secondary" onClick={()=>setCompare(!compare)}>{compare?'Hide comparison':'Compare A / B'}</button></div>
       {response&&<><div className="es-section-title"><h3>Fixture response</h3><span className="es-green">200 OK</span></div><pre className="es-code">{response}</pre></>}
       {compare&&<div className="es-comparison"><div className="es-section-title"><h3>Identity comparison</h3><span>SIMULATED</span></div><div><span>Account A</span><code>id: user_a</code></div><div><span>Account B</span><code>id: user_b</code></div><p>Different identities returned. This fixture does not establish an authorization vulnerability.</p></div>}
       <p className="es-micro">Native HTTP execution, session isolation and real comparisons are planned.</p>
      </>}
      {page==='Tools' && <>
       <div className="es-section-title"><h3>Base64 decoder</h3><span className="es-green">WORKING LOCALLY</span></div><label className="es-editor-label">INPUT<textarea aria-label="Decoder input" value={codecInput} onChange={e=>setCodecInput(e.target.value)}/></label><div className="es-actionrow"><button className="es-button" onClick={()=>codec(false)}>Encode</button><button className="es-button es-secondary" onClick={()=>codec(true)}>Decode</button></div><label className="es-editor-label">OUTPUT<textarea aria-label="Decoder output" readOnly value={codecOutput} placeholder="Your result appears here"/></label><div className="es-infobox"><Icon name="code"/><p><b>Unicode-aware. On-device.</b><br/>This utility actually runs in the preview. No data is sent anywhere.</p></div>
       <div className="es-section-title"><h3>Next in the toolkit</h3><span>PLANNED</span></div><div className="es-planned-list"><span>URL / hex / HTML transforms</span><span>Token analysis & JWT verification</span><span>Structured JSON comparison</span></div>
      </>}
      {page==='Findings' && <>
       <div className="es-infobox"><Icon name="flag"/><p><b>Evidence before conclusions.</b><br/>No automated vulnerabilities have been discovered in this preview.</p></div><label className="es-editor-label">SESSION NOTE<textarea aria-label="Finding note" value={note} onChange={e=>setNote(e.target.value)} placeholder="Describe an observation or next test…" maxLength={1000}/></label><button className="es-button" disabled={!note.trim()} onClick={()=>{setNotes([...notes,note.trim()]);setNote('');notify('Note added in memory. Reloading clears it.');}}>Add demo note</button><div className="es-section-title"><h3>Investigation notes</h3><span>{notes.length} NOTES</span></div>{notes.length===0?<div className="es-empty">No notes yet.<br/>Start with an observation worth following.</div>:notes.map((n,i)=><div className="es-note" key={i}><span>NOTE {String(i+1).padStart(2,'0')}</span><p>{n}</p></div>)}<p className="es-micro">Persistence, redacted exports and retest tracking are planned.</p>
      </>}
     </div>
     <nav className="es-bottomnav" aria-label="App navigation">{nav.map(([p,icon])=><button key={p} onClick={()=>go(p)} className={page===p?'active':''} aria-current={page===p?'page':undefined}><Icon name={icon} size={20}/><span>{p==='Workspace'?'Home':p==='API Lab'?'API Lab':p}</span></button>)}</nav>
     {selected!==null&&<div ref={drawerRef} className="es-drawer" role="dialog" aria-modal="true" aria-label="Request details"><div className="es-drawer-head"><div><span className="es-eyebrow">REQUEST DETAILS · FIXTURE</span><h3>{requests[selected].path}</h3></div><button className="es-iconbtn" onClick={()=>setSelected(null)} aria-label="Close request details"><Icon name="close"/></button></div><p className="es-micro">{requests[selected].method} · {requests[selected].status} · {requests[selected].time}</p><pre className="es-code">{requests[selected].body}</pre><button className="es-button" onClick={()=>sendToEditor(selected)}>Send to API Lab <Icon name="arrow"/></button></div>}
     {toast&&<div className="es-toast" role="status"><span>{toast}</span><button aria-label="Dismiss notice" onClick={()=>setToast('')}><Icon name="close" size={15}/></button></div>}
     <div className="es-homebar"/>
    </section>
    <aside className="es-context">
      <div className="es-eyebrow">THE WORKFLOW / 01</div><h2>From a tap<br/>to the truth.</h2><p>Connected tools. Clear context.<br/>Made for a smaller screen.</p>
      <ol className="es-workflow"><li><span>01</span><div><b>Inspect</b><p>Elements, cookies and storage.</p></div></li><li><span>02</span><div><b>Intercept</b><p>Understand both sides of a request.</p></div></li><li><span>03</span><div><b>Investigate</b><p>Replay, compare and record evidence.</p></div></li></ol>
      <div className="es-boundary"><span className="es-statusdot"/><b>Visibility, honestly.</b><p>Other-app HTTPS inspection depends on certificate trust and protocol support. Encrypted contents may remain unavailable.</p></div>
      <div className="es-palette"><i style={{background:'#5BE0D6'}}/><i style={{background:'#FFB86B'}}/><i style={{background:'#E7EEF6'}}/><i style={{background:'#253849'}}/><span>MIDNIGHT / SIGNAL</span></div>
      <span className="es-context-foot">WEB + API FIRST<br/>CLOUD ON THE ROADMAP</span>
    </aside>
   </main>
   <footer className="es-board-footer"><span>ESUITE / INTERACTION STUDY</span><span>NATIVE ANDROID PRODUCT · RESPONSIVE WEB CONCEPT</span><span>DESIGNED FOR INVESTIGATION ↗</span></footer>
 </div>;
};
