const element = (id) => document.getElementById(id);
const entryDialog = element("dialog");
const entryForm = element("entry-form");
const closeEntry = element("close");
const submitEntry = element("confirm");
const revealed = element("revealed");
const fileDialog = element("file-dialog");
const fileForm = element("file-form");

let activeStore;
let aliases = [];
let entryAction;
let selectedAlias;
let openingFile;
let entryPending = false;
let filePending = false;
let revealTimeout;

function warnBeforeLeaving(event) {
    event.preventDefault();
    event.returnValue = "";
}

function setModified(modified) {
    activeStore.modified = modified;
    element("file-status").textContent = modified ? "Unsaved changes" : "No unsaved changes";
    if (modified) {
        window.addEventListener("beforeunload", warnBeforeLeaving);
    } else {
        window.removeEventListener("beforeunload", warnBeforeLeaving);
    }
}

function displayWorkspace(store) {
    activeStore = store;
    aliases = [];
    element("filter").value = "";
    element("start").hidden = Boolean(store);
    element("workspace").hidden = !store;
    if (store) {
        element("filename").textContent = store.filename;
        setModified(store.modified);
    } else {
        element("filename").textContent = "";
        element("file-status").textContent = "";
        window.removeEventListener("beforeunload", warnBeforeLeaving);
    }
    renderEntries();
}

async function request(path, { method = "GET", body, download = false } = {}) {
    const headers = { Accept: download ? "application/octet-stream, application/problem+json" : "application/json" };
    if (activeStore) {
        headers["X-Keystore-Id"] = activeStore.id;
    }
    if (body !== undefined && !(body instanceof FormData)) {
        headers["Content-Type"] = "application/json";
        body = JSON.stringify(body);
    }

    const response = await fetch(path, { method, headers, body, cache: "no-store" });
    if (!response.ok) {
        if (response.status === 413) {
            throw new Error("Choose a keystore of 16 MB or smaller.");
        }
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.detail || "The keystore request failed.");
    }
    if (response.status === 204) {
        return undefined;
    }
    return download ? response.blob() : response.json();
}

function renderEntries() {
    const query = element("filter").value.trim().toLowerCase();
    const results = aliases.filter((alias) => alias.toLowerCase().includes(query));
    const rows = results.map((alias) => {
        const row = document.createElement("tr");
        row.insertCell().textContent = alias;
        const actions = document.createElement("div");
        actions.className = "row-actions";
        for (const label of ["Reveal", "Update", "Delete"]) {
            const button = document.createElement("button");
            button.className = label === "Delete" ? "quiet danger" : "quiet";
            button.type = "button";
            button.textContent = label;
            button.setAttribute("aria-label", label + " " + alias);
            button.addEventListener("click", () => openEntryDialog(label, alias));
            actions.append(button);
        }
        row.insertCell().append(actions);
        return row;
    });
    element("entries").replaceChildren(...rows);
    element("count").textContent = String(aliases.length);
    element("entry-table").hidden = results.length === 0;
    element("empty").hidden = results.length !== 0;
    element("empty").textContent = query ? "No matching aliases." : "No password entries yet.";
}

async function refreshEntries() {
    const store = activeStore;
    const entries = await request("api/credentials");
    if (activeStore === store) {
        aliases = entries;
        renderEntries();
    }
}

function openFileDialog(open) {
    openingFile = open;
    fileForm.reset();
    element("file-dialog-title").textContent = open ? "Open a keystore" : "Create a keystore";
    element("upload-field").hidden = !open;
    element("upload").required = open;
    element("filename-field").hidden = open;
    element("new-filename").required = !open;
    element("confirm-password-field").hidden = open;
    element("confirm-password").required = !open;
    element("file-error").hidden = true;
    element("file-submit").textContent = open ? "Open keystore" : "Create keystore";
    fileDialog.showModal();
    element(open ? "upload" : "new-filename").focus();
}

fileForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const password = element("store-password").value;
    const error = element("file-error");
    error.hidden = true;
    if (!openingFile && password !== element("confirm-password").value) {
        error.textContent = "The keystore passwords do not match.";
        error.hidden = false;
        element("confirm-password").focus();
        return;
    }

    filePending = element("file-submit").disabled = element("file-cancel").disabled = true;
    try {
        let info;
        if (openingFile) {
            const payload = new FormData();
            payload.append("file", element("upload").files[0]);
            payload.append("password", password);
            info = await request("api/store/open", { method: "POST", body: payload });
        } else {
            const filename = element("new-filename").value;
            info = await request("api/store/new", { method: "POST", body: { filename, password } });
        }
        displayWorkspace(info);
        fileDialog.close();
        element("status").textContent = openingFile ? "Keystore opened." : "Keystore created.";
        await refreshEntries();
    } catch (failure) {
        if (fileDialog.open) {
            error.textContent = failure.message;
            error.hidden = false;
        } else {
            element("status").textContent = failure.message;
        }
    } finally {
        element("store-password").value = "";
        element("confirm-password").value = "";
        filePending = element("file-submit").disabled = element("file-cancel").disabled = false;
    }
});

fileDialog.addEventListener("cancel", (event) => {
    if (filePending) {
        event.preventDefault();
    }
});
fileDialog.addEventListener("close", () => fileForm.reset());
element("file-cancel").addEventListener("click", () => fileDialog.close());
element("open-store").addEventListener("click", () => openFileDialog(true));
element("new-store").addEventListener("click", () => openFileDialog(false));

function allowDiscard() {
    return !activeStore?.modified || window.confirm("Discard unsaved keystore changes and return home?");
}

function setWorkspacePending(pending) {
    element("back-home").disabled = pending;
    element("save-store").disabled = pending;
    element("add").disabled = pending;
    element("entry-table").inert = pending;
}

element("back-home").addEventListener("click", async () => {
    if (!allowDiscard()) {
        return;
    }
    setWorkspacePending(true);
    try {
        await request("api/store", { method: "DELETE" });
        displayWorkspace(undefined);
        element("status").textContent = "Keystore closed.";
        element("open-store").focus();
    } catch (failure) {
        element("status").textContent = failure.message;
    } finally {
        setWorkspacePending(false);
    }
});

element("save-store").addEventListener("click", async () => {
    const store = activeStore;
    setWorkspacePending(true);
    try {
        const file = await request("api/store/file", { method: "POST", download: true });
        const url = URL.createObjectURL(file);
        const link = document.createElement("a");
        link.href = url;
        link.download = store.filename;
        document.body.append(link);
        link.click();
        link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 30000);
        if (activeStore === store) {
            setModified(false);
            element("status").textContent = "Keystore download started.";
        }
    } catch (failure) {
        element("status").textContent = failure.message;
    } finally {
        setWorkspacePending(false);
    }
});

function openEntryDialog(operation, alias = "") {
    entryAction = operation;
    selectedAlias = alias;
    entryForm.reset();
    revealed.textContent = "";
    revealed.hidden = true;
    element("error").hidden = true;

    const creating = operation === "Create";
    const editing = creating || operation === "Update";
    element("dialog-title").textContent = creating ? "New credential" : operation + " credential";
    element("dialog-detail").textContent = creating ? "Enter an alias and its secret value." : alias;
    element("alias-field").hidden = !creating;
    element("alias").required = creating;
    element("value-field").hidden = !editing;
    element("value").required = editing;
    closeEntry.textContent = "Cancel";
    submitEntry.hidden = false;
    submitEntry.textContent = editing ? "Save entry" : operation;
    submitEntry.classList.toggle("danger", operation === "Delete");
    entryDialog.showModal();
    if (editing) {
        element(creating ? "alias" : "value").focus();
    } else {
        closeEntry.focus();
    }
}

entryForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    entryPending = submitEntry.disabled = closeEntry.disabled = true;
    const error = element("error");
    error.hidden = true;
    const value = element("value").value;
    const path = "api/credentials/" + encodeURIComponent(selectedAlias);
    try {
        if (entryAction === "Reveal") {
            const credential = await request(path + "/reveal", { method: "POST" });
            revealed.textContent = credential.value;
            revealed.hidden = false;
            submitEntry.hidden = true;
            closeEntry.textContent = "Hide";
            revealTimeout = window.setTimeout(() => entryDialog.close(), 30000);
            return;
        }
        if (entryAction === "Create") {
            await request("api/credentials", { method: "POST", body: { alias: element("alias").value, value } });
        } else if (entryAction === "Update") {
            await request(path, { method: "PUT", body: { value } });
        } else {
            await request(path, { method: "DELETE" });
        }
        setModified(true);
        entryDialog.close();
        const result = { Create: "Credential added.", Update: "Credential updated.", Delete: "Credential deleted." };
        element("status").textContent = result[entryAction];
        await refreshEntries();
    } catch (failure) {
        if (entryDialog.open) {
            error.textContent = failure.message;
            error.hidden = false;
        } else {
            element("status").textContent = "Change saved to the workspace. Reload the page to refresh the list.";
        }
    } finally {
        entryPending = submitEntry.disabled = closeEntry.disabled = false;
    }
});

entryDialog.addEventListener("cancel", (event) => {
    if (entryPending) {
        event.preventDefault();
    }
});
entryDialog.addEventListener("close", () => {
    window.clearTimeout(revealTimeout);
    revealed.textContent = "";
    revealed.hidden = true;
    element("value").value = "";
});
closeEntry.addEventListener("click", () => entryDialog.close());
element("filter").addEventListener("input", renderEntries);
element("add").addEventListener("click", () => openEntryDialog("Create"));

async function initialize() {
    try {
        const store = await request("api/store");
        displayWorkspace(store);
        element("open-store").disabled = element("new-store").disabled = false;
        if (store) {
            await refreshEntries();
        }
    } catch (failure) {
        element("status").textContent = failure.message;
    }
}

initialize();
