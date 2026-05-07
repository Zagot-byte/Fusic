window.FUSIC = {
  currentTrack: null,
  queue: [],
  history: [],
  isPlaying: false,
  position: 0,
  volume: parseFloat(localStorage.getItem('fusic_volume') || '0.8'),
  shuffle: localStorage.getItem('fusic_shuffle') === 'true',
  repeat: localStorage.getItem('fusic_repeat') || 'off',
  source: 'ytm',
  lyrics: null,
  lyricsIndex: 0
};

const API_BASE = 'http://localhost:8001';
const API_YT = 'http://localhost:8002';
const API_KEY = 'dev';

// DOM Elements
const audio = document.getElementById('fusic-audio');
const playBtn = document.getElementById('btn-play-pause');
const playIcon = document.getElementById('icon-play');
const pauseIcon = document.getElementById('icon-pause');
const btnPrev = document.getElementById('btn-prev');
const btnNext = document.getElementById('btn-next');
const btnShuffle = document.getElementById('btn-shuffle');
const btnRepeat = document.getElementById('btn-repeat');
const seekSlider = document.getElementById('seek-slider');
const volSlider = document.getElementById('volume-slider');
const timeCurrent = document.getElementById('time-current');
const timeTotal = document.getElementById('time-total');

const searchInput = document.getElementById('search-input');
const searchResults = document.getElementById('search-results');

const npArt = document.getElementById('np-art');
const npTitle = document.getElementById('np-title');
const npArtist = document.getElementById('np-artist');

const lyricCurrent = document.getElementById('lyric-current');
const lyricNext = document.getElementById('lyric-next');

// Mobile specific additions
const miniPlayer = document.getElementById('mini-player');
const mpArt = document.getElementById('mp-art');
const mpTitle = document.getElementById('mp-title');
const mpArtist = document.getElementById('mp-artist');
const mpPlayBtn = document.getElementById('btn-mp-play-pause');
const mpPlayIcon = document.getElementById('mp-icon-play');
const mpPauseIcon = document.getElementById('mp-icon-pause');
const nowPlayingPanel = document.getElementById('now-playing-panel');
const btnClosePlayer = document.getElementById('btn-close-player');

// Dummy Data
const DUMMY_TRACKS = [
  { videoId: "dQw4w9WgXcQ", title: "Never Gonna Give You Up", artist: "Rick Astley", album: "Whenever You Need Somebody", thumbnail: "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", duration: 213 },
  { videoId: "yKNxeF4KMsY", title: "Yellow", artist: "Coldplay", album: "Parachutes", thumbnail: "https://i.ytimg.com/vi/yKNxeF4KMsY/hqdefault.jpg", duration: 269 },
  { videoId: "fJ9rUzIMcZQ", title: "Bohemian Rhapsody", artist: "Queen", album: "A Night at the Opera", thumbnail: "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg", duration: 354 }
];

function init() {
  audio.volume = window.FUSIC.volume;
  if(volSlider) volSlider.value = window.FUSIC.volume;
  
  updateRepeatUI();
  updateShuffleUI();
  
  if (DUMMY_TRACKS.length > 0) {
    window.FUSIC.queue = [...DUMMY_TRACKS];
    loadTrack(DUMMY_TRACKS[0]);
  }
  
  setupListeners();
  setupListeners();
  renderHome();
}

let searchTimeout = null;
if (searchInput) {
  searchInput.addEventListener('input', (e) => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
      performSearch(e.target.value);
    }, 500);
  });
}

async function performSearch(query) {
  if (!query.trim()) {
    searchResults.innerHTML = '';
    return;
  }
  try {
    const res = await fetch(`${API_YT}/search?q=${encodeURIComponent(query)}`, { headers: { 'X-API-Key': API_KEY } });
    if (!res.ok) throw new Error('Search failed');
    const data = await res.json();
    renderSearchResults(data);
  } catch (e) {
    console.error(e);
  }
}

function renderSearchResults(data) {
  searchResults.innerHTML = '';
  // ytmusicapi search usually returns a list of dictionaries with category, resultType, videoId, title, artists, thumbnails
  // We'll map them to tracks
  if (!Array.isArray(data)) return;
  const tracks = data.filter(item => item.resultType === 'song' || item.resultType === 'video');
  
  tracks.forEach(t => {
    const track = {
      videoId: t.videoId,
      title: t.title,
      artist: t.artists ? t.artists.map(a => a.name).join(', ') : 'Unknown',
      thumbnail: t.thumbnails && t.thumbnails.length > 0 ? t.thumbnails[t.thumbnails.length - 1].url : 'fusic-logo-192.png',
      duration: t.duration_seconds || 0
    };
    
    const div = document.createElement('div');
    div.className = 'track-item';
    div.style.display = 'flex';
    div.style.alignItems = 'center';
    div.style.gap = '12px';
    div.style.padding = '8px';
    div.style.cursor = 'pointer';
    div.style.borderBottom = '1px solid var(--border)';
    
    div.innerHTML = `
      <img src="${track.thumbnail}" style="width:48px;height:48px;object-fit:cover;border-radius:4px;">
      <div style="flex:1;">
        <div style="font-weight:bold;color:var(--text);">${track.title}</div>
        <div style="font-size:0.85em;color:var(--text-dim);">${track.artist}</div>
      </div>
    `;
    
    div.addEventListener('click', () => {
      // Add to queue if not present
      if (!window.FUSIC.queue.find(q => q.videoId === track.videoId)) {
        window.FUSIC.queue.push(track);
      }
      loadTrack(track);
      play();
      if (window.innerWidth <= 768) {
        nowPlayingPanel.classList.add('open');
      }
    });
    
    searchResults.appendChild(div);
  });
}

function getCachedUrl(videoId) {
  try {
    const cache = JSON.parse(localStorage.getItem('fusic_cache_urls') || '{}');
    return cache[videoId];
  } catch(e) { return null; }
}

function cacheUrl(videoId, url, expires) {
  try {
    const cache = JSON.parse(localStorage.getItem('fusic_cache_urls') || '{}');
    cache[videoId] = { url, expires: expires ? expires * 1000 : Date.now() + 86400000 };
    localStorage.setItem('fusic_cache_urls', JSON.stringify(cache));
  } catch(e) {}
}

async function resolveTrack(videoId) {
  const cached = getCachedUrl(videoId);
  if (cached && cached.expires > Date.now()) return cached.url;
  
  try {
    const res = await fetch(`${API_BASE}/resolve/${videoId}`, { headers: { 'X-API-Key': API_KEY } });
    if (!res.ok) throw new Error('Resolve failed');
    const data = await res.json();
    cacheUrl(videoId, data.url, data.expires);
    return data.url;
  } catch (e) {
    console.warn('Backend resolve failed, expected without connected backend.');
    return null;
  }
}

async function loadTrack(track) {
  window.FUSIC.currentTrack = track;
  npTitle.textContent = track.title;
  npArtist.textContent = track.artist;
  npArt.src = track.thumbnail;
  
  // Update mini player
  mpTitle.textContent = track.title;
  mpArtist.textContent = track.artist;
  mpArt.src = track.thumbnail;
  miniPlayer.style.display = 'flex'; // show when track loaded
  
  timeTotal.textContent = formatTime(track.duration);
  seekSlider.max = track.duration;
  seekSlider.value = 0;
  updateSeekUI(0);
  
  const url = await resolveTrack(track.videoId);
  if (url) {
    audio.src = url;
    if (window.FUSIC.isPlaying) audio.play();
  }
}

function play() {
  window.FUSIC.isPlaying = true;
  if (audio.src) audio.play().catch(e => console.warn('Play intercepted (expected)', e));
  playIcon.style.display = 'none';
  pauseIcon.style.display = 'block';
  mpPlayIcon.style.display = 'none';
  mpPauseIcon.style.display = 'block';
  playBtn.classList.add('active-pressed');
}

function pause() {
  window.FUSIC.isPlaying = false;
  audio.pause();
  playIcon.style.display = 'block';
  pauseIcon.style.display = 'none';
  mpPlayIcon.style.display = 'block';
  mpPauseIcon.style.display = 'none';
  playBtn.classList.remove('active-pressed');
}

function next() {
  const q = window.FUSIC.queue;
  if (q.length === 0) return;
  const currentIndex = q.findIndex(t => t.videoId === window.FUSIC.currentTrack?.videoId);
  let nextIdx = currentIndex + 1;
  if (window.FUSIC.shuffle) {
    nextIdx = Math.floor(Math.random() * q.length);
  } else if (nextIdx >= q.length) {
    nextIdx = 0; // loop
  }
  loadTrack(q[nextIdx]);
  if (window.FUSIC.isPlaying) play();
}

function prev() {
  if (audio.currentTime > 3) {
    seek(0);
  } else {
    const q = window.FUSIC.queue;
    if (q.length === 0) return;
    const currentIndex = q.findIndex(t => t.videoId === window.FUSIC.currentTrack?.videoId);
    let prevIdx = currentIndex - 1;
    if (prevIdx < 0) prevIdx = q.length - 1;
    loadTrack(q[prevIdx]);
    if (window.FUSIC.isPlaying) play();
  }
}

function seek(seconds) {
  audio.currentTime = seconds;
  updateSeekUI(seconds);
}

function updateSeekUI(seconds) {
  seekSlider.value = seconds;
  timeCurrent.textContent = formatTime(seconds);
  const max = seekSlider.max || 100;
  const percent = max > 0 ? (seconds / max) * 100 : 0;
  seekSlider.style.setProperty('--seek-before-width', `${percent}%`);
}

function formatTime(s) {
  if (isNaN(s)) return "0:00";
  const mins = Math.floor(s / 60);
  const secs = Math.floor(s % 60);
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

function setupListeners() {
  playBtn.addEventListener('click', () => { window.FUSIC.isPlaying ? pause() : play(); });
  mpPlayBtn.addEventListener('click', (e) => { 
    e.stopPropagation(); // prevent mini-player click
    window.FUSIC.isPlaying ? pause() : play(); 
  });
  
  btnNext.addEventListener('click', next);
  btnPrev.addEventListener('click', prev);
  
  btnShuffle.addEventListener('click', () => {
    window.FUSIC.shuffle = !window.FUSIC.shuffle;
    localStorage.setItem('fusic_shuffle', window.FUSIC.shuffle);
    updateShuffleUI();
  });
  
  btnRepeat.addEventListener('click', () => {
    if (window.FUSIC.repeat === 'off') window.FUSIC.repeat = 'all';
    else if (window.FUSIC.repeat === 'all') window.FUSIC.repeat = 'one';
    else window.FUSIC.repeat = 'off';
    localStorage.setItem('fusic_repeat', window.FUSIC.repeat);
    updateRepeatUI();
  });
  
  seekSlider.addEventListener('input', (e) => { updateSeekUI(e.target.value); });
  seekSlider.addEventListener('change', (e) => { audio.currentTime = e.target.value; });
  
  audio.addEventListener('timeupdate', () => {
    updateSeekUI(audio.currentTime);
    syncLyrics(audio.currentTime);
  });
  
  audio.addEventListener('ended', () => {
    if (window.FUSIC.repeat === 'one') { seek(0); play(); }
    else next();
  });

  if(volSlider) {
    volSlider.addEventListener('input', (e) => {
      audio.volume = e.target.value;
      window.FUSIC.volume = audio.volume;
      localStorage.setItem('fusic_volume', audio.volume);
    });
  }

  // Mobile Player Overlay
  miniPlayer.addEventListener('click', () => {
    nowPlayingPanel.classList.add('open');
  });
  btnClosePlayer.addEventListener('click', () => {
    nowPlayingPanel.classList.remove('open');
  });

  // Mobile Bottom Sheet Navigation
  const fab = document.getElementById('mobile-fab');
  const bottomSheet = document.getElementById('bottom-sheet');
  const overlay = document.getElementById('bottom-sheet-overlay');

  if(fab && bottomSheet && overlay) {
    fab.addEventListener('click', () => {
      bottomSheet.classList.add('open');
      overlay.classList.add('active');
    });
    overlay.addEventListener('click', () => {
      bottomSheet.classList.remove('open');
      overlay.classList.remove('active');
    });

    const sheetBtns = document.querySelectorAll('.sheet-btn');
    sheetBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        sheetBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        bottomSheet.classList.remove('open');
        overlay.classList.remove('active');
        
        const page = btn.getAttribute('data-page');
        if (page !== 'now-playing') {
          switchPage(page);
        } else {
          nowPlayingPanel.classList.add('open');
        }
      });
    });
  }

  // Desktop Navigation
  const navBtns = document.querySelectorAll('.nav-btn');
  navBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      navBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      switchPage(btn.getAttribute('data-page'));
    });
  });

  const brand = document.getElementById('brand-fusic');
  if (brand) {
    brand.addEventListener('click', () => location.reload());
  }
}

function updateShuffleUI() { btnShuffle.classList.toggle('active', window.FUSIC.shuffle); }
function updateRepeatUI() { btnRepeat.classList.toggle('active', window.FUSIC.repeat !== 'off'); }

function switchPage(pageId) {
  const pages = document.querySelectorAll('.page-view');
  pages.forEach(p => p.classList.remove('active'));
  const target = document.getElementById('page-' + pageId);
  if(target) target.classList.add('active');
}

async function renderHome() {
  const shelfQuick = document.getElementById('shelf-quick');
  const shelfRec = document.getElementById('shelf-recommended');
  const shelfMixed = document.getElementById('shelf-mixed');
  
  if(!shelfQuick) return;
  
  const generateCards = (target, items) => {
    target.innerHTML = '';
    items.forEach(t => {
      // mapping ytmusicapi format to our track format
      const track = {
        videoId: t.videoId,
        title: t.title,
        artist: t.artists ? t.artists.map(a => a.name).join(', ') : 'Unknown',
        thumbnail: t.thumbnails && t.thumbnails.length > 0 ? t.thumbnails[t.thumbnails.length - 1].url : 'fusic-logo-192.png',
        duration: t.duration_seconds || 0
      };

      const card = document.createElement('div');
      card.className = 'card';
      card.innerHTML = `
        <img class="card-img" src="${track.thumbnail}" alt="art">
        <div class="card-title">${track.title}</div>
        <div class="card-subtitle">${track.artist}</div>
      `;
      card.addEventListener('click', () => {
        if (!window.FUSIC.queue.find(q => q.videoId === track.videoId)) {
          window.FUSIC.queue.push(track);
        }
        loadTrack(track);
        play();
        if (window.innerWidth <= 768) {
          nowPlayingPanel.classList.add('open');
        }
      });
      target.appendChild(card);
    });
  };

  try {
    const res = await fetch(`${API_YT}/home`, { headers: { 'X-API-Key': API_KEY } });
    if (res.ok) {
      const data = await res.json();
      // data is usually an array of shelves from ytmusicapi
      // let's grab random sections or just first few for our UI
      if (Array.isArray(data) && data.length > 0) {
        if (data[0] && data[0].contents) generateCards(shelfQuick, data[0].contents.filter(c => c.videoId).slice(0, 10));
        if (data[1] && data[1].contents) generateCards(shelfRec, data[1].contents.filter(c => c.videoId).slice(0, 10));
        if (data[2] && data[2].contents) generateCards(shelfMixed, data[2].contents.filter(c => c.videoId).slice(0, 10));
        return; // we loaded from API successfully
      }
    }
  } catch (e) {
    console.warn("Could not fetch home from API, using dummy tracks");
  }

  // Fallback to DUMMY_TRACKS
  const fallbackRender = (target) => {
    DUMMY_TRACKS.forEach(t => {
      const card = document.createElement('div');
      card.className = 'card';
      card.innerHTML = `
        <img class="card-img" src="${t.thumbnail}" alt="art">
        <div class="card-title">${t.title}</div>
        <div class="card-subtitle">${t.artist}</div>
      `;
      card.addEventListener('click', () => {
        loadTrack(t);
        play();
        if (window.innerWidth <= 768) {
          nowPlayingPanel.classList.add('open');
        }
      });
      target.appendChild(card);
    });
  };

  fallbackRender(shelfQuick);
  fallbackRender(shelfRec);
  fallbackRender(shelfMixed);
}

function syncLyrics(time) {
  if(!window.FUSIC.lyrics) {
    lyricCurrent.textContent = '';
    lyricNext.textContent = '';
    return;
  }
}

window.addEventListener('DOMContentLoaded', init);
