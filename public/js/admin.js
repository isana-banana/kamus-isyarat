const loginPanel = document.getElementById("loginPanel");
const adminPanel = document.getElementById("adminPanel");
const loginForm = document.getElementById("loginForm");
const loginMessage = document.getElementById("loginMessage");
const addForm = document.getElementById("addDictionaryForm");
const adminMessage = document.getElementById("adminMessage");
const dictionaryList = document.getElementById("adminDictionaryList");

async function api(url, options = {}) {
  const response = await fetch(url, {
    credentials: "same-origin",
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    }
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data.error || "Terjadi kesalahan.");
  }

  return data;
}

function showMessage(element, message, type = "") {
  element.textContent = message;
  element.className = "message " + type;
}

function showAdmin() {
  loginPanel.classList.add("hidden");
  adminPanel.classList.remove("hidden");
  loadDictionaries();
}

function showLogin() {
  adminPanel.classList.add("hidden");
  loginPanel.classList.remove("hidden");
}

async function checkLogin() {
  try {
    await api("/api/admin/me");
    showAdmin();
  } catch {
    showLogin();
  }
}

loginForm.addEventListener("submit", async event => {
  event.preventDefault();
  showMessage(loginMessage, "Memeriksa kata sandi...");

  const password = document.getElementById("adminPassword").value;

  try {
    await api("/api/admin/login", {
      method: "POST",
      body: JSON.stringify({ password })
    });

    document.getElementById("adminPassword").value = "";
    showMessage(loginMessage, "Login berhasil.", "success");
    showAdmin();
  } catch (error) {
    showMessage(loginMessage, error.message, "error");
  }
});

document.getElementById("logoutButton").addEventListener("click", async () => {
  try {
    await api("/api/admin/logout", { method: "POST" });
  } finally {
    showLogin();
  }
});

addForm.addEventListener("submit", async event => {
  event.preventDefault();

  const name = document.getElementById("dictionaryName").value.trim();
  const folderUrl = document.getElementById("folderUrl").value.trim();

  const submitButton = addForm.querySelector("button[type=submit]");
  submitButton.disabled = true;
  showMessage(adminMessage, "Memeriksa folder dan file kamus.json...");

  try {
    await api("/api/admin/dictionaries", {
      method: "POST",
      body: JSON.stringify({ name, folderUrl })
    });

    addForm.reset();
    showMessage(adminMessage, "Kamus berhasil ditambahkan.", "success");
    await loadDictionaries();
  } catch (error) {
    showMessage(adminMessage, error.message, "error");
  } finally {
    submitButton.disabled = false;
  }
});

async function loadDictionaries() {
  dictionaryList.textContent = "Memuat daftar kamus...";

  try {
    const data = await api("/api/admin/dictionaries");
    dictionaryList.replaceChildren();

    if (!data.items || data.items.length === 0) {
      dictionaryList.textContent = "Belum ada kamus terdaftar.";
      return;
    }

    for (const item of data.items) {
      const card = document.createElement("div");
      card.className = "admin-item";

      const title = document.createElement("strong");
      title.textContent = item.name;
      card.appendChild(title);

      const info = document.createElement("small");
      info.textContent = (item.wordCount || 0) +
        " kata · " + (item.active ? "Aktif" : "Nonaktif");
      card.appendChild(info);

      const actions = document.createElement("div");
      actions.className = "admin-actions";

      const toggle = document.createElement("button");
      toggle.type = "button";
      toggle.className = "button-secondary";
      toggle.textContent = item.active ? "Nonaktifkan" : "Aktifkan";
      toggle.addEventListener("click", () =>
        updateDictionary(item.id, { active: !item.active })
      );
      actions.appendChild(toggle);

      const rename = document.createElement("button");
      rename.type = "button";
      rename.className = "button-secondary";
      rename.textContent = "Ubah nama";
      rename.addEventListener("click", async () => {
        const newName = prompt("Nama kamus baru:", item.name);
        if (newName === null || !newName.trim()) return;
        await updateDictionary(item.id, { name: newName.trim() });
      });
      actions.appendChild(rename);

      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "danger-button";
      remove.textContent = "Hapus";
      remove.addEventListener("click", async () => {
        if (!confirm("Hapus kamus ini dari daftar aplikasi? File di Google Drive tidak ikut dihapus.")) return;
        await deleteDictionary(item.id);
      });
      actions.appendChild(remove);

      card.appendChild(actions);
      dictionaryList.appendChild(card);
    }
  } catch (error) {
    dictionaryList.textContent = error.message;
  }
}

async function updateDictionary(id, changes) {
  try {
    await api("/api/admin/dictionaries/" + encodeURIComponent(id), {
      method: "PATCH",
      body: JSON.stringify(changes)
    });
    showMessage(adminMessage, "Data kamus diperbarui.", "success");
    await loadDictionaries();
  } catch (error) {
    showMessage(adminMessage, error.message, "error");
  }
}

async function deleteDictionary(id) {
  try {
    await api("/api/admin/dictionaries/" + encodeURIComponent(id), {
      method: "DELETE"
    });
    showMessage(adminMessage, "Kamus dihapus dari daftar.", "success");
    await loadDictionaries();
  } catch (error) {
    showMessage(adminMessage, error.message, "error");
  }
}

checkLogin();
