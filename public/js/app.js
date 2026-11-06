const dictionarySelect = document.getElementById("dictionarySelect");
const searchInput = document.getElementById("searchInput");
const clearSearch = document.getElementById("clearSearch");
const wordList = document.getElementById("wordList");
const resultInfo = document.getElementById("resultInfo");
const selectedWord = document.getElementById("selectedWord");
const selectedDefinition = document.getElementById("selectedDefinition");
const imageBox = document.getElementById("imageBox");
const videoBox = document.getElementById("videoBox");

let currentWords = [];
let selectedKey = null;

async function api(url) {
  const response = await fetch(url);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || "Gagal mengambil data.");
  return data;
}

function driveImageUrl(fileId) {
  return "https://drive.google.com/thumbnail?id=" +
    encodeURIComponent(fileId) + "&sz=w1000";
}

function drivePreviewUrl(fileId) {
  return "https://drive.google.com/file/d/" +
    encodeURIComponent(fileId) + "/preview";
}

function safeText(value) {
  return String(value ?? "");
}

function getFilteredWords() {
  const tokens = searchInput.value
    .trim()
    .toLocaleLowerCase("id")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 5);

  if (!tokens.length) return currentWords;

  return currentWords.filter(([word, item]) => {
    const searchable = (word + " " + safeText(item.definisi))
      .toLocaleLowerCase("id");
    return tokens.every(token => searchable.includes(token));
  });
}

function showSelectedWord(key, item) {
  selectedKey = key;
  selectedWord.textContent = key;
  selectedDefinition.textContent = safeText(item.definisi);

  imageBox.replaceChildren();
  videoBox.replaceChildren();

  if (item.gambar) {
    const image = document.createElement("img");
    image.className = "word-image";
    image.src = driveImageUrl(item.gambar);
    image.alt = "Gambar isyarat " + key;
    image.loading = "lazy";
    image.onerror = () => {
      imageBox.textContent = "Gambar tidak dapat ditampilkan. Periksa ID dan izin Google Drive.";
    };
    imageBox.appendChild(image);
  } else {
    imageBox.innerHTML = '<div class="media-placeholder">Gambar belum tersedia</div>';
  }

  if (item.video) {
    const frame = document.createElement("iframe");
    frame.className = "video-frame";
    frame.src = drivePreviewUrl(item.video);
    frame.title = "Video isyarat " + key;
    frame.allow = "autoplay; encrypted-media; picture-in-picture";
    frame.allowFullscreen = true;
    frame.loading = "lazy";
    videoBox.appendChild(frame);

    const link = document.createElement("a");
    link.className = "video-link";
    link.href = drivePreviewUrl(item.video);
    link.target = "_blank";
    link.rel = "noopener";
    link.textContent = "Buka video";
    videoBox.appendChild(link);
  } else {
    videoBox.innerHTML = '<div class="media-placeholder">Video belum tersedia</div>';
  }

  renderWordList();
}

function renderWordList() {
  const filtered = getFilteredWords();
  resultInfo.textContent = filtered.length +
    " kata ditemukan dari " + currentWords.length + " kata.";

  wordList.replaceChildren();

  if (!filtered.length) {
    const empty = document.createElement("div");
    empty.className = "empty-state";
    empty.textContent = "Kata tidak ditemukan.";
    wordList.appendChild(empty);
    selectedKey = null;
    selectedWord.textContent = "Kata tidak ditemukan";
    selectedDefinition.textContent = "Coba kata pencarian yang lain.";
    imageBox.innerHTML = '<div class="media-placeholder">Gambar isyarat</div>';
    videoBox.innerHTML = '<div class="media-placeholder">Video isyarat</div>';
    return;
  }

  for (const [word, item] of filtered) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "word-item" + (word === selectedKey ? " active" : "");
    button.textContent = word;
    button.setAttribute("aria-pressed", word === selectedKey ? "true" : "false");
    button.addEventListener("click", () => showSelectedWord(word, item));
    wordList.appendChild(button);
  }

  if (!filtered.some(([word]) => word === selectedKey)) {
    showSelectedWord(filtered[0][0], filtered[0][1]);
  }
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
      wordList.textContent = "Belum ada kamus yang ditambahkan admin.";
      resultInfo.textContent = "";
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
    wordList.textContent = error.message;
    resultInfo.textContent = "Gagal memuat daftar kamus.";
  }
}

async function loadDictionary(id) {
  if (!id) return;

  wordList.textContent = "Memuat isi kamus...";
  resultInfo.textContent = "";
  currentWords = [];
  selectedKey = null;

  try {
    const data = await api("/api/dictionary/" + encodeURIComponent(id));
    const manifest = data.manifest;

    currentWords = Object.entries(manifest.kata || {})
      .sort((a, b) => a[0].localeCompare(b[0], "id"));

    searchInput.value = "";

    if (currentWords.length) {
      showSelectedWord(currentWords[0][0], currentWords[0][1]);
    } else {
      renderWordList();
    }
  } catch (error) {
    currentWords = [];
    wordList.textContent = error.message;
    resultInfo.textContent = "Kamus gagal dimuat.";
  }
}

dictionarySelect.addEventListener("change", () => {
  loadDictionary(dictionarySelect.value);
});

searchInput.addEventListener("input", renderWordList);

clearSearch.addEventListener("click", () => {
  searchInput.value = "";
  renderWordList();
  searchInput.focus();
});

loadCatalog();
