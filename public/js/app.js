const dictionarySelect = document.getElementById("dictionarySelect");
const searchInput = document.getElementById("searchInput");
const wordList = document.getElementById("wordList");
const resultInfo = document.getElementById("resultInfo");

let currentWords = [];

async function api(url) {
  const response = await fetch(url);
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data.error || "Gagal mengambil data.");
  }

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

function renderWords() {
  const query = searchInput.value.trim().toLocaleLowerCase("id");

  const filtered = currentWords.filter(([word, item]) => {
    const definition = safeText(item.definisi).toLocaleLowerCase("id");
    return word.toLocaleLowerCase("id").includes(query) ||
      definition.includes(query);
  });

  resultInfo.textContent = filtered.length +
    " kata ditemukan dari " + currentWords.length + " kata.";

  wordList.replaceChildren();

  if (!filtered.length) {
    const empty = document.createElement("div");
    empty.className = "empty-state";
    empty.textContent = "Kata tidak ditemukan.";
    wordList.appendChild(empty);
    return;
  }

  for (const [word, item] of filtered) {
    const card = document.createElement("article");
    card.className = "word-card";

    const heading = document.createElement("h3");
    heading.textContent = word;
    card.appendChild(heading);

    if (item.definisi) {
      const definition = document.createElement("p");
      definition.textContent = item.definisi;
      card.appendChild(definition);
    }

    if (item.gambar) {
      const image = document.createElement("img");
      image.className = "word-image";
      image.src = driveImageUrl(item.gambar);
      image.alt = "Gambar isyarat " + word;
      image.loading = "lazy";
      image.onerror = () => {
        image.alt = "Gambar tidak dapat ditampilkan. Periksa izin Google Drive.";
      };
      card.appendChild(image);
    }

    if (item.video) {
      const frame = document.createElement("iframe");
      frame.className = "video-frame";
      frame.src = drivePreviewUrl(item.video);
      frame.title = "Video isyarat " + word;
      frame.allow = "autoplay; encrypted-media; picture-in-picture";
      frame.allowFullscreen = true;
      card.appendChild(frame);

      const link = document.createElement("a");
      link.className = "video-link";
      link.href = drivePreviewUrl(item.video);
      link.target = "_blank";
      link.rel = "noopener";
      link.textContent = "Buka video";
      card.appendChild(link);
    }

    wordList.appendChild(card);
  }
}

async function loadCatalog() {
  try {
    const data = await api("/api/catalog");
    dictionarySelect.replaceChildren();

    if (!data.items || data.items.length === 0) {
      const option = document.createElement("option");
      option.value = "";
      option.textContent = "Belum ada kamus tersedia";
      dictionarySelect.appendChild(option);
      wordList.innerHTML = "";
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

  try {
    const data = await api("/api/dictionary/" + encodeURIComponent(id));
    const manifest = data.manifest;

    currentWords = Object.entries(manifest.kata || {})
      .sort((a, b) => a[0].localeCompare(b[0], "id"));

    searchInput.value = "";
    renderWords();
  } catch (error) {
    currentWords = [];
    wordList.textContent = error.message;
    resultInfo.textContent = "Kamus gagal dimuat.";
  }
}

dictionarySelect.addEventListener("change", () => {
  loadDictionary(dictionarySelect.value);
});

searchInput.addEventListener("input", renderWords);

loadCatalog();