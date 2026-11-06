
const API = "/api";
const $ = id => document.getElementById(id);

let catalog = [];
let dictionaryData = null;
let selectedWord = null;

function extractDriveId(value) {
  const s = String(value || "").trim();

  if (/^[a-zA-Z0-9_-]{10,}$/.test(s)) return s;

  const patterns = [
    /\/folders\/([a-zA-Z0-9_-]+)/,
    /\/file\/d\/([a-zA-Z0-9_-]+)/,
    /\/d\/([a-zA-Z0-9_-]+)/,
    /[?&]id=([a-zA-Z0-9_-]+)/
  ];

  for (const pattern of patterns) {
    const match = s.match(pattern);
    if (match) return match[1];
  }

  return "";
}

function drivePreview(id) {
  return `https://drive.google.com/file/d/${encodeURIComponent(id)}/preview`;
}

function driveImage(id) {
  return `https://drive.google.com/thumbnail?id=${encodeURIComponent(id)}&sz=w1200`;
}

function clearSelection() {
  selectedWord = null;

  $("selectedWord").textContent = "";
  $("definitionText").textContent = "Pilih salah satu kata pada daftar.";

  $("videoContainer").className = "media-content empty-media";
  $("videoContainer").textContent = "Video akan tampil di sini";

  $("imageContainer").className = "media-content empty-media";
  $("imageContainer").textContent = "Gambar akan tampil di sini";
}

function renderCatalog() {
  const box = $("dictionaryOptions");
  box.replaceChildren();

  if (!catalog.length) {
    const empty = document.createElement("div");
    empty.className = "dictionary-option";
    empty.textContent = "Belum ada kamus yang diterbitkan.";
    box.appendChild(empty);
    $("dictionaryName").textContent = "Kamus Isyarat";
    return;
  }

  catalog.forEach(item => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "dictionary-option";
    button.textContent = item.name;

    button.addEventListener("click", () => loadDictionary(item.id));

    box.appendChild(button);
  });
}

function renderWords() {
  const list = $("wordList");
  list.replaceChildren();

  const words = Object.keys(dictionaryData?.kata || {})
    .sort((a, b) => a.localeCompare(b, "id"));

  const query = $("searchInput").value.trim().toLocaleUpperCase("id-ID");

  // Pencarian dapat menggunakan maksimal lima kata.
  const terms = query.split(/\s+/).filter(Boolean).slice(0, 5);

  const filtered = terms.length
    ? words.filter(word =>
        terms.every(term => word.toLocaleUpperCase("id-ID").includes(term))
      )
    : words;

  filtered.forEach(word => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "word-chip";
    button.textContent = word;
    button.setAttribute("aria-pressed", String(selectedWord === word));

    button.addEventListener("click", () => selectWord(word));

    list.appendChild(button);
  });

  $("listStatus").textContent = filtered.length
    ? `${filtered.length} kata ditampilkan.`
    : "Kata tidak ditemukan.";

  // Jika kata terpilih tidak lagi cocok dengan pencarian,
  // bersihkan detailnya.
  if (selectedWord && !filtered.includes(selectedWord)) {
    clearSelection();
  }
}

function selectWord(word) {
  const item = dictionaryData?.kata?.[word];
  if (!item) return;

  selectedWord = word;

  $("selectedWord").textContent = word;
  $("definitionText").textContent = item.definisi || "Belum ada definisi.";

  const videoId = extractDriveId(item.video);
  const imageId = extractDriveId(item.gambar);

  const video = $("videoContainer");
  const image = $("imageContainer");

  video.replaceChildren();
  image.replaceChildren();

  video.className = "media-content";
  image.className = "media-content";

  if (videoId) {
    const iframe = document.createElement("iframe");
    iframe.src = drivePreview(videoId);
    iframe.title = `Video isyarat ${word}`;
    iframe.allow = "autoplay; fullscreen; picture-in-picture";
    iframe.allowFullscreen = true;
    video.appendChild(iframe);
  } else {
    video.className = "media-content empty-media";
    video.textContent = "Video belum tersedia.";
  }

  if (imageId) {
    const img = document.createElement("img");
    img.src = driveImage(imageId);
    img.alt = `Gambar isyarat ${word}`;
    img.loading = "lazy";

    img.onerror = () => {
      image.className = "media-content empty-media";
      image.textContent =
        "Gambar tidak dapat dimuat. Periksa ID dan izin Google Drive.";
    };

    image.appendChild(img);
  } else {
    image.className = "media-content empty-media";
    image.textContent = "Gambar belum tersedia.";
  }

  renderWords();
}

async function loadDictionary(id) {
  const item = catalog.find(x => x.id === id);
  if (!item) return;

  $("dictionaryMenu").hidden = true;
  $("dictionaryToggle").setAttribute("aria-expanded", "false");

  $("dictionaryName").textContent = item.name;
  $("listStatus").textContent = "Memuat daftar kata...";

  $("searchInput").value = "";
  dictionaryData = null;
  clearSelection();

  try {
    const response = await fetch(
      `${API}/dictionary/${encodeURIComponent(id)}`
    );

    const body = await response.json();

    if (!response.ok) {
      throw new Error(body.error || "Gagal memuat kamus.");
    }

    // Worker mengirim { id, name, manifest }.
    // Format ini juga mendukung respons lama yang langsung berisi kata.
    dictionaryData = body.manifest || body;

    if (!dictionaryData.kata) {
      throw new Error("Data kata tidak ditemukan dalam kamus.json.");
    }

    $("listHint").textContent =
      dictionaryData.nama || item.name;

    // Tidak memilih kata secara otomatis.
    clearSelection();
    renderWords();

  } catch (error) {
    dictionaryData = null;
    $("wordList").replaceChildren();
    $("listStatus").textContent = error.message;
    clearSelection();
  }
}

async function init() {
  try {
    const response = await fetch(`${API}/catalog`);
    const body = await response.json();

    if (!response.ok) {
      throw new Error(body.error || "Katalog tidak dapat dimuat.");
    }

    catalog = body.items || [];
    renderCatalog();

    // Kamus pertama dimuat, tetapi tidak ada kata yang dipilih.
    if (catalog.length) {
      await loadDictionary(catalog[0].id);
    } else {
      $("listStatus").textContent = "Belum ada kamus tersedia.";
    }

  } catch (error) {
    $("dictionaryName").textContent = "Kamus Isyarat";
    $("listStatus").textContent = error.message;
  }
}

$("dictionaryToggle").addEventListener("click", () => {
  const menu = $("dictionaryMenu");
  menu.hidden = !menu.hidden;

  $("dictionaryToggle").setAttribute(
    "aria-expanded",
    String(!menu.hidden)
  );
});

$("searchInput").addEventListener("input", renderWords);

$("clearSearch").addEventListener("click", () => {
  $("searchInput").value = "";
  renderWords();
  $("searchInput").focus();
});

init();
