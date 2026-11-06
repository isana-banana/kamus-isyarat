const dictionarySelect = document.getElementById("dictionarySelect");
const searchInput = document.getElementById("searchInput");
const clearSearch = document.getElementById("clearSearch");
const wordList = document.getElementById("wordList");
const resultInfo = document.getElementById("resultInfo");
const wordDefinition = document.getElementById("wordDefinition");
const imageWrap = document.getElementById("imageWrap");
const videoWrap = document.getElementById("videoWrap");

let currentWords = [];
let selectedWord = null;

async function api(url) {
  const response = await fetch(url);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || "Gagal mengambil data.");
  return data;
}

// Menerima ID file maupun link berbagi Google Drive.
function driveFileId(value) {
  const input = String(value || "").trim();
  if (!input) return "";
  if (/^[a-zA-Z0-9_-]{10,}$/.test(input)) return input;

  try {
    const url = new URL(input);
    const byPath = url.pathname.match(/\/file\/d\/([a-zA-Z0-9_-]+)/);
    if (byPath) return byPath[1];
    const byQuery = url.searchParams.get("id");
    if (byQuery) return byQuery;
  } catch {}
  return input;
}

function driveImageUrl(value) {
  const id = driveFileId(value);
  return id ? "https://drive.google.com/thumbnail?id=" + encodeURIComponent(id) + "&sz=w1200" : "";
}

// Video dialirkan melalui Cloudflare Worker, bukan Google Drive Preview.
function driveVideoUrl(value) {
  const id = driveFileId(value);
  return id ? "/api/media/" + encodeURIComponent(id) : "";
}

function safeText(value) {
  return String(value ?? "");
}

function showPlaceholder(container, message) {
  container.replaceChildren();
  const placeholder = document.createElement("span");
  placeholder.className = "media-placeholder";
  placeholder.textContent = message;
  container.appendChild(placeholder);
}

function showSelectedWord(word, item) {
  selectedWord = word;
  wordDefinition.textContent = safeText(item.definisi) || "Belum ada keterangan.";

  imageWrap.replaceChildren();
  if (item.gambar) {
    const image = document.createElement("img");
    image.className = "word-image";
    image.src = driveImageUrl(item.gambar);
    image.alt = "Gambar isyarat " + word;
    image.onerror = () => showPlaceholder(imageWrap, "Gambar tidak dapat ditampilkan. Periksa ID dan izin Google Drive.");
    imageWrap.appendChild(image);
  } else {
    showPlaceholder(imageWrap, "Belum ada gambar untuk kata ini.");
  }

  videoWrap.replaceChildren();
  if (item.video) {
    const video = document.createElement("video");
    video.className = "word-video";
    video.controls = true;
    video.preload = "metadata";
    video.playsInline = true;
    video.setAttribute("controlsList", "nodownload");
    video.src = driveVideoUrl(item.video);
    video.onerror = () => showPlaceholder(videoWrap, "Video gagal dimuat. Periksa ID, izin Drive, dan format MP4 (H.264/AAC).");
    videoWrap.appendChild(video);
  } else {
    showPlaceholder(videoWrap, "Belum ada video untuk kata ini.");
  }

  for (const button of wordList.querySelectorAll(".word-button")) {
    const active = button.dataset.word === word;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  }
}

function getFilteredWords() {
  const query = searchInput.value.trim().toLocaleLowerCase("id");
  const terms = query.split(/\s+/).filter(Boolean).slice(0, 5);
  return currentWords.filter(([word, item]) => {
    const normalizedWord = word.toLocaleLowerCase("id");
    const definition = safeText(item.definisi).toLocaleLowerCase("id");
    if (!terms.length) return true;
    return terms.some(term => normalizedWord.includes(term) || definition.includes(term));
  });
}

function renderWords() {
  const filtered = getFilteredWords();
  resultInfo.textContent = filtered.length + " dari " + currentWords.length + " kata";
  wordList.replaceChildren();

  if (!filtered.length) {
    showPlaceholder(wordList, "Kata tidak ditemukan.");
    wordDefinition.textContent = "Tidak ada kata yang cocok dengan pencarian.";
    showPlaceholder(imageWrap, "Tidak ada gambar.");
    showPlaceholder(videoWrap, "Tidak ada video.");
    selectedWord = null;
    return;
  }

  for (const [word, item] of filtered) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "word-button";
    button.textContent = word;
    button.dataset.word = word;
    button.setAttribute("aria-pressed", "false");
    button.addEventListener("click", () => showSelectedWord(word, item));
    wordList.appendChild(button);
  }

  const stillVisible = filtered.find(([word]) => word === selectedWord);
  const next = stillVisible || filtered[0];
  showSelectedWord(next[0], next[1]);
}

async function loadCatalog() {
  try {
    const data = await api("/api/catalog");
    dictionarySelect.replaceChildren();

    if (!data.items || data.items.length === 0) {
      const option = document.createElement("option");
      option.value = "";
      option.textContent = "Belum ada kamus";
      dictionarySelect.appendChild(option);
      resultInfo.textContent = "";
      showPlaceholder(wordList, "Belum ada kamus yang ditambahkan admin.");
      return;
    }

    for (const item of data.items) {
      const option = document.createElement("option");
      option.value = item.id;
      option.textContent = item.name;
      dictionarySelect.appendChild(option);
    }
    await loadDictionary(dictionarySelect.value);
  } catch (error) {
    showPlaceholder(wordList, error.message);
    resultInfo.textContent = "Gagal memuat daftar kamus.";
  }
}

async function loadDictionary(id) {
  if (!id) return;
  resultInfo.textContent = "Memuat...";
  showPlaceholder(wordList, "Memuat isi kamus...");
  try {
    const data = await api("/api/dictionary/" + encodeURIComponent(id));
    const manifest = data.manifest || {};
    currentWords = Object.entries(manifest.kata || {}).sort((a, b) => a[0].localeCompare(b[0], "id"));
    selectedWord = null;
    searchInput.value = "";
    renderWords();
  } catch (error) {
    currentWords = [];
    showPlaceholder(wordList, error.message);
    resultInfo.textContent = "Kamus gagal dimuat.";
  }
}

dictionarySelect.addEventListener("change", () => loadDictionary(dictionarySelect.value));
searchInput.addEventListener("input", renderWords);
clearSearch.addEventListener("click", () => {
  searchInput.value = "";
  renderWords();
  searchInput.focus();
});

loadCatalog();
