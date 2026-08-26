(() => {
  'use strict';

  const SHELL_VERSION='1.0.0';
  const APP_URL='https://script.google.com/macros/s/AKfycbyd7UHyQFJA4SsFZuKWmAO___NnfGXq0oNB0M0NWnG2hhLmPHcKTL_ck4yDgB4IqSkOnQ/exec';
  const FRAME_LOAD_TIMEOUT_MS=30000;
  const READY_FALLBACK_MS=12000;
  const RESUME_REFRESH_MS=5*60*1000;
  const LOG_KEY='eloBarShellDiagnosticsV1';
  const shell=document.getElementById('shell');
  const frame=document.getElementById('eloApp');
  const splashText=document.getElementById('splashText');
  const retryBtn=document.getElementById('retryBtn');
  const networkBadge=document.getElementById('networkBadge');
  const updateBadge=document.getElementById('updateBadge');

  const sessionId=crypto.randomUUID?.()||`S-${Date.now().toString(36)}-${Math.random().toString(36).slice(2,8)}`;
  const startedAt=performance.now();
  let loadTimer=0;
  let fallbackTimer=0;
  let frameLoaded=false;
  let appReady=false;
  let hiddenAt=0;
  let retrying=false;
  let swRefreshing=false;

  function log(event,detail={}){
    const item={at:new Date().toISOString(),event,sessionId,shellVersion:SHELL_VERSION,...detail};
    try{
      const rows=JSON.parse(localStorage.getItem(LOG_KEY)||'[]');
      rows.push(item);
      localStorage.setItem(LOG_KEY,JSON.stringify(rows.slice(-30)));
    }catch(_){}
  }

  function appSrc(extra={}){
    const url=new URL(APP_URL);
    url.searchParams.set('shell','android');
    url.searchParams.set('shellVersion',SHELL_VERSION);
    url.searchParams.set('shellSession',sessionId);
    Object.entries(extra).forEach(([k,v])=>url.searchParams.set(k,String(v)));
    return url.toString();
  }

  function setSplash(text,canRetry=false){
    if(text)splashText.textContent=text;
    retryBtn.hidden=!canRetry;
  }

  function setState(state){
    shell.dataset.state=state;
  }

  function reveal(reason='ready'){
    if(appReady&&shell.dataset.state==='ready')return;
    appReady=reason==='ready'||appReady;
    clearTimeout(loadTimer);clearTimeout(fallbackTimer);
    setState(reason==='ready'?'ready':'fallback-ready');
    retryBtn.hidden=true;
    log('frame_revealed',{reason,ms:Math.round(performance.now()-startedAt)});
  }

  function showLoading(text='Abrindo Elo Bar…'){
    setState('booting');
    setSplash(text,false);
  }

  function loadFrame(reason='initial'){
    if(retrying)return;
    retrying=true;
    frameLoaded=false;appReady=false;
    clearTimeout(loadTimer);clearTimeout(fallbackTimer);
    showLoading(reason==='initial'?'Abrindo Elo Bar…':'Reconectando ao Elo Bar…');
    frame.src=appSrc(reason==='initial'?{}:{retry:Date.now()});
    log('frame_load_start',{reason});

    loadTimer=setTimeout(()=>{
      if(appReady)return;
      setSplash(navigator.onLine?'O Elo Bar demorou mais que o esperado.':'Sem conexão com a internet.',true);
      log('frame_timeout',{online:navigator.onLine});
    },FRAME_LOAD_TIMEOUT_MS);
    setTimeout(()=>{retrying=false},350);
  }

  function isAppOrigin(origin){
    try{
      const host=new URL(origin).hostname;
      return host==='script.google.com'||host.endsWith('.googleusercontent.com');
    }catch(_){return false}
  }

  function postToApp(type,detail={}){
    if(!frame.contentWindow)return;
    frame.contentWindow.postMessage({source:'elo-bar-shell',type,sessionId,shellVersion:SHELL_VERSION,...detail},'*');
  }

  function setNetworkState(){
    const online=navigator.onLine;
    networkBadge.hidden=online;
    networkBadge.textContent='Sem conexão';
    if(!online){
      log('offline');
      if(!appReady)setSplash('Sem conexão com a internet.',true);
    }else{
      log('online');
      if(appReady)postToApp('ELO_SHELL_REFRESH_DATA',{reason:'network-restored'});
      else if(frameLoaded)setSplash('Reconectando ao Elo Bar…',false);
    }
  }

  function handleAppMessage(event){
    if(event.source!==frame.contentWindow||!isAppOrigin(event.origin))return;
    const data=event.data||{};
    if(data.source!=='elo-bar-app')return;

    if(data.type==='ELO_APP_READY'){
      appReady=true;
      log('app_ready',{appVersion:data.appVersion||'',route:data.route||'',appMs:data.appMs||0,totalMs:Math.round(performance.now()-startedAt)});
      reveal('ready');
      return;
    }
    if(data.type==='ELO_APP_REFRESH_RESULT'){
      log('app_refresh_result',{ok:!!data.ok,reason:data.reason||'',message:data.message||''});
      if(!data.ok){
        networkBadge.textContent='Falha ao atualizar';
        networkBadge.hidden=false;
        setTimeout(setNetworkState,3500);
      }
      return;
    }
    if(data.type==='ELO_APP_ROUTE'){
      log('route',{route:data.route||''});
      return;
    }
    if(data.type==='ELO_APP_EXTERNAL'){
      log('external_link',{scheme:data.scheme||''});
      return;
    }
    if(data.type==='ELO_APP_PONG'){
      log('app_pong',{route:data.route||'',online:data.online!==false});
    }
  }

  frame.addEventListener('load',()=>{
    frameLoaded=true;
    log('frame_load_event',{ms:Math.round(performance.now()-startedAt)});
    clearTimeout(fallbackTimer);
    fallbackTimer=setTimeout(()=>{
      if(!appReady)reveal('fallback');
    },READY_FALLBACK_MS);
  });

  frame.addEventListener('error',()=>{
    log('frame_error');
    setSplash('Não foi possível abrir o Elo Bar.',true);
  });

  retryBtn.addEventListener('click',()=>loadFrame('manual-retry'));
  window.addEventListener('message',handleAppMessage);
  window.addEventListener('online',setNetworkState);
  window.addEventListener('offline',setNetworkState);

  document.addEventListener('visibilitychange',()=>{
    if(document.hidden){
      hiddenAt=Date.now();
      log('background');
      return;
    }
    const away=hiddenAt?Date.now()-hiddenAt:0;
    log('foreground',{awayMs:away});
    hiddenAt=0;
    if(appReady&&away>=RESUME_REFRESH_MS){
      postToApp('ELO_SHELL_REFRESH_DATA',{reason:'resume',awayMs:away});
    }else if(appReady){
      postToApp('ELO_SHELL_PING',{reason:'resume'});
    }
  });

  window.addEventListener('error',event=>log('shell_error',{message:String(event.message||'Erro').slice(0,240),source:String(event.filename||'').split('/').pop(),line:event.lineno||0}));
  window.addEventListener('unhandledrejection',event=>log('shell_rejection',{message:String(event.reason?.message||event.reason||'Promise rejeitada').slice(0,240)}));

  async function registerServiceWorker(){
    if(!('serviceWorker'in navigator))return;
    try{
      const registration=await navigator.serviceWorker.register('./sw.js',{scope:'./'});
      log('sw_registered');
      registration.update().catch(()=>{});
      if(registration.waiting)registration.waiting.postMessage({type:'ELO_SHELL_SKIP_WAITING'});
      registration.addEventListener('updatefound',()=>{
        const worker=registration.installing;
        if(!worker)return;
        updateBadge.hidden=false;
        worker.addEventListener('statechange',()=>{
          if(worker.state==='installed'){
            updateBadge.textContent='Nova versão pronta';
            setTimeout(()=>{updateBadge.hidden=true},2600);
          }
        });
      });
      navigator.serviceWorker.addEventListener('controllerchange',()=>{
        if(swRefreshing)return;
        swRefreshing=true;
        log('sw_controller_changed');
        setTimeout(()=>{updateBadge.hidden=true;swRefreshing=false},1200);
      });
    }catch(error){
      log('sw_error',{message:String(error?.message||error).slice(0,240)});
    }
  }

  setNetworkState();
  registerServiceWorker();
  loadFrame('initial');
})();
