(function () {
  const apiBase = `${window.location.origin}/api/v1`;
  let token = localStorage.getItem("teacher.token") || "";
  let selectedParticipantId = null;
  let selectedParticipantQuestId = null;
  let selectedQuestId = null;
  let selectedLocation = null;
  let cachedTeacherQuests = [];
  let cachedQuestTeachers = [];
  let cachedLocations = [];
  let cachedTasks = [];
  let quizOptions = [];
  let editQuizOptions = [];
  let questLocationNamesCache = {};

  const TASK_TYPE_LABELS = {
    QUIZ: "Викторина",
    INFO: "Инфо-блок",
    QUESTION: "Ответ текстом"
  };

  const el = (id) => document.getElementById(id);
  const authCard = el("authCard");
  const dashboard = el("dashboard");
  const tbody = el("progressTbody");
  const participantCard = el("participantCard");
  const participantAnswers = el("participantAnswers");
  const pointsList = el("pointsList");
  const selectedPointCard = el("selectedPointCard");
  const pointTasksList = el("pointTasksList");
  const questsList = el("questsList");
  const selectedQuestCard = el("selectedQuestCard");
  const questTeachersList = el("questTeachersList");
  const tabButtons = Array.from(document.querySelectorAll(".tab-btn[data-tab-target]"));
  const tabPanels = Array.from(document.querySelectorAll(".tab-panel"));

  const authName = el("authName");
  const authPassword = el("authPassword");
  const authMessage = el("authMessage");
  const hintMessage = el("hintMessage");
  const recommendationMessage = el("recommendationMessage");
  const questMessage = el("questMessage");
  const locationMessage = el("locationMessage");
  const taskMessage = el("taskMessage");
  const teacherManageMessage = el("teacherManageMessage");

  function currentLocationLabel(title) {
    if (!title) return "Нет данных";
    return title;
  }

  function progressPercent(completed, total) {
    if (!total || total <= 0) return 0;
    return Math.max(0, Math.min(100, Math.round((completed / total) * 100)));
  }

  function renderProgressBar(completed, total, compact) {
    const percent = progressPercent(completed, total);
    const sizeClass = compact ? " progress-block-compact" : "";
    return `
      <div class="progress-block${sizeClass}">
        <div class="progress-meta">
          <strong>${completed}/${total}</strong>
          <span class="muted">${percent}%</span>
        </div>
        <div class="progress-track">
          <div class="progress-fill" style="width:${percent}%"></div>
        </div>
      </div>
    `;
  }

  function latestAnswersByTask(items) {
    const map = new Map();
    (items || []).forEach((item) => {
      const prev = map.get(item.taskId);
      if (!prev) {
        map.set(item.taskId, item);
        return;
      }
      const prevTs = Date.parse(prev.createdAt || "") || 0;
      const nextTs = Date.parse(item.createdAt || "") || 0;
      if (nextTs >= prevTs) {
        map.set(item.taskId, item);
      }
    });
    return map;
  }

  async function getQuestTaskOverview(questId) {
    if (!questId) return [];
    const locations = await api(`/teacher/quests/${encodeURIComponent(questId)}/locations`);
    const locationList = Array.isArray(locations) ? locations : [];
    const groups = await Promise.all(locationList.map(async (location) => {
      try {
        const tasks = await api(`/teacher/locations/${encodeURIComponent(location.locationId)}/tasks`);
        return (Array.isArray(tasks) ? tasks : []).map((task, index) => ({
          taskId: task.taskId,
          title: task.title,
          taskType: task.taskType,
          maxScore: task.maxScore,
          locationTitle: location.title,
          locationPosition: Number(location.position) || 0,
          taskOrder: index
        }));
      } catch (_e) {
        return [];
      }
    }));
    return groups
      .flat()
      .sort((a, b) =>
        (a.locationPosition - b.locationPosition) ||
        (a.taskOrder - b.taskOrder) ||
        String(a.title).localeCompare(String(b.title), "ru")
      );
  }

  function renderParticipantTaskStatus(task, answer) {
    if (answer && answer.isCorrect) {
      return { label: "Пройдено", className: "task-status-ok" };
    }
    if (answer && !answer.isCorrect) {
      return { label: "Есть ошибка", className: "task-status-danger" };
    }
    if (task.taskType === "INFO") {
      return { label: "Инфо-блок", className: "task-status-muted" };
    }
    return { label: "Нет ответа", className: "task-status-warn" };
  }

  function minutesFromNow(iso) {
    if (!iso) return 9999;
    const ts = Date.parse(iso);
    if (Number.isNaN(ts)) return 9999;
    return Math.floor((Date.now() - ts) / 60000);
  }

  function formatAgo(iso) {
    const m = minutesFromNow(iso);
    if (m >= 9999) return "нет данных";
    if (m < 1) return "только что";
    if (m < 60) return `${m} мин назад`;
    const h = Math.floor(m / 60);
    return `${h} ч назад`;
  }

  function setAuthState(ok) {
    if (ok) {
      authCard.classList.add("hidden");
      dashboard.classList.remove("hidden");
    } else {
      authCard.classList.remove("hidden");
      dashboard.classList.add("hidden");
    }
  }

  function applyTheme(theme) {
    document.body.setAttribute("data-theme", theme);
    localStorage.setItem("teacher.theme", theme);
    el("btnTheme").textContent = theme === "dark" ? "Светлая тема" : "Тёмная тема";
  }

  function toggleTheme() {
    const current = document.body.getAttribute("data-theme") || "light";
    applyTheme(current === "dark" ? "light" : "dark");
  }

  function setActiveTab(tabId) {
    tabButtons.forEach((button) => {
      const active = button.getAttribute("data-tab-target") === tabId;
      button.classList.toggle("active", active);
    });
    tabPanels.forEach((panel) => {
      panel.classList.toggle("hidden", panel.id !== tabId);
    });
    localStorage.setItem("teacher.activeTab", tabId);
  }

  async function api(path, options) {
    const isFormData = options && options.body instanceof FormData;
    const headers = Object.assign({}, (options && options.headers) || {});
    if (!isFormData && !("Content-Type" in headers)) {
      headers["Content-Type"] = "application/json";
    }
    if (token) headers.Authorization = `Bearer ${token}`;
    const resp = await fetch(`${apiBase}${path}`, Object.assign({}, options, { headers }));
    const bodyText = await resp.text();
    let body = null;
    try { body = bodyText ? JSON.parse(bodyText) : null; } catch (_e) {}
    if (!resp.ok) {
      const msg = body && body.message ? body.message : `HTTP ${resp.status}`;
      throw new Error(msg);
    }
    return body;
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function generateQrId() {
    const stamp = Date.now().toString(36).toUpperCase();
    const suffix = Math.random().toString(36).slice(2, 8).toUpperCase();
    return `${stamp}-${suffix}`;
  }

  function nextLocationPosition() {
    if (!cachedLocations.length) return 1;
    return Math.max(...cachedLocations.map((loc) => Number(loc.position) || 1)) + 1;
  }

  function taskType() {
    return el("taskType").value;
  }

  function toggleTaskTypeFields() {
    const isQuiz = taskType() === "QUIZ";
    const isQuestion = taskType() === "QUESTION";
    el("quizOptionsSection").classList.toggle("hidden", !isQuiz);
    el("questionAnswerField").classList.toggle("hidden", !isQuestion);
  }

  function toggleEditTaskTypeFields() {
    const type = el("editTaskType").value;
    el("editQuizOptionsSection").classList.toggle("hidden", type !== "QUIZ");
    el("editQuestionAnswerField").classList.toggle("hidden", type !== "QUESTION");
  }

  function openQuestEditor(quest) {
    el("editQuestTitle").value = quest.title || "";
    el("editQuestDescription").value = quest.description || "";
    el("editQuestInstitution").value = quest.institutionName || "";
    el("editQuestIsActive").checked = !!quest.isActive;
    el("questEditMessage").textContent = "";
    el("questEditor").classList.remove("hidden");
  }

  function closeQuestEditor() {
    el("questEditor").classList.add("hidden");
    el("questEditMessage").textContent = "";
  }

  function mapTaskToEditor(task) {
    el("editTaskId").value = task.taskId;
    el("editTaskTitle").value = task.title || "";
    el("editTaskDescription").value = task.description || "";
    el("editTaskType").value = task.taskType || "INFO";
    el("editTaskMaxScore").value = String(task.maxScore || 1);
    el("editTaskCorrectAnswer").value = task.correctAnswer || "";
    el("editTaskMediaUrl").value = task.mediaUrl || "";
    el("editTaskMediaType").value = task.mediaType || "";
    editQuizOptions = (task.options || []).map((text, index) => ({
      id: `edit-opt-${Date.now()}-${Math.random().toString(36).slice(2, 7)}-${index}`,
      text,
      isCorrect: Number(task.correctOptionIndex) === index
    }));
    if (el("editTaskType").value === "QUIZ" && editQuizOptions.length === 0) {
      editQuizOptions = [
        { id: `edit-opt-${Date.now()}-a`, text: "", isCorrect: true },
        { id: `edit-opt-${Date.now()}-b`, text: "", isCorrect: false }
      ];
    }
    renderEditQuizOptions();
    toggleEditTaskTypeFields();
    el("taskEditMessage").textContent = "";
  }

  function openTaskEditor(task) {
    mapTaskToEditor(task);
    el("taskEditor").classList.remove("hidden");
  }

  function closeTaskEditor() {
    el("taskEditor").classList.add("hidden");
    el("taskEditMessage").textContent = "";
    el("editTaskId").value = "";
    el("editTaskMediaFile").value = "";
    editQuizOptions = [];
    renderEditQuizOptions();
  }

  function addQuizOption(text = "") {
    quizOptions.push({
      id: `opt-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      text,
      isCorrect: quizOptions.length === 0
    });
    renderQuizOptions();
  }

  function renderQuizOptions() {
    const list = el("quizOptionsList");
    list.innerHTML = "";
    quizOptions.forEach((option) => {
      const row = document.createElement("div");
      row.className = `quiz-option-row${option.isCorrect ? " is-correct" : ""}`;
      row.innerHTML = `
        <input type="text" value="${escapeHtml(option.text)}" placeholder="Вариант ответа">
        <button type="button" class="btn btn-ghost" data-make-correct="${option.id}">Сделать верным</button>
        <button type="button" class="btn btn-ghost" data-delete-option="${option.id}">Удалить</button>
      `;
      const input = row.querySelector("input");
      input.addEventListener("input", (e) => {
        option.text = e.target.value;
      });
      row.querySelector("[data-make-correct]").addEventListener("click", () => {
        quizOptions.forEach((item) => {
          item.isCorrect = item.id === option.id;
        });
        renderQuizOptions();
      });
      row.querySelector("[data-delete-option]").addEventListener("click", () => {
        quizOptions = quizOptions.filter((item) => item.id !== option.id);
        if (!quizOptions.some((item) => item.isCorrect) && quizOptions.length > 0) {
          quizOptions[0].isCorrect = true;
        }
        renderQuizOptions();
      });
      list.appendChild(row);
    });
  }

  function renderEditQuizOptions() {
    const list = el("editQuizOptionsList");
    list.innerHTML = "";
    editQuizOptions.forEach((option) => {
      const row = document.createElement("div");
      row.className = `quiz-option-row${option.isCorrect ? " is-correct" : ""}`;
      row.innerHTML = `
        <input type="text" value="${escapeHtml(option.text)}" placeholder="Вариант ответа">
        <button type="button" class="btn btn-ghost" data-edit-make-correct="${option.id}">Сделать верным</button>
        <button type="button" class="btn btn-ghost" data-edit-delete-option="${option.id}">Удалить</button>
      `;
      const input = row.querySelector("input");
      input.addEventListener("input", (e) => {
        option.text = e.target.value;
      });
      row.querySelector("[data-edit-make-correct]").addEventListener("click", () => {
        editQuizOptions.forEach((item) => {
          item.isCorrect = item.id === option.id;
        });
        renderEditQuizOptions();
      });
      row.querySelector("[data-edit-delete-option]").addEventListener("click", () => {
        editQuizOptions = editQuizOptions.filter((item) => item.id !== option.id);
        if (!editQuizOptions.some((item) => item.isCorrect) && editQuizOptions.length > 0) {
          editQuizOptions[0].isCorrect = true;
        }
        renderEditQuizOptions();
      });
      list.appendChild(row);
    });
  }

  function updateSelectedPointCard() {
    const btnDownload = el("btnDownloadQrPrint");
    if (!selectedLocation) {
      selectedPointCard.textContent = "Выберите точку в списке выше.";
      btnDownload.disabled = true;
      cachedTasks = [];
      renderLocationTasks();
      return;
    }
    selectedPointCard.innerHTML = `
      <p><strong>Точка:</strong> ${escapeHtml(selectedLocation.title)}</p>
      <p><strong>ID точки:</strong> ${escapeHtml(selectedLocation.locationId)}</p>
      <p><strong>Позиция:</strong> ${selectedLocation.position}</p>
      <p><strong>QR-ID:</strong> <code>${escapeHtml(selectedLocation.qrCode)}</code></p>
      <p><strong>Заданий:</strong> ${selectedLocation.tasksCount || 0}</p>
    `;
    btnDownload.disabled = false;
  }

  function renderTaskMediaBlock(task) {
    if (!task.mediaUrl) return "";
    const rawUrl = String(task.mediaUrl);
    const mediaType = String(task.mediaType || "").toUpperCase();
    const isVideo =
      mediaType === "VIDEO" ||
      rawUrl.startsWith("data:video/") ||
      /\.mp4(\?|$)|\.webm(\?|$)|\.ogg(\?|$)/i.test(rawUrl);
    const url = rawUrl.startsWith("data:") ? rawUrl : `${rawUrl}${rawUrl.includes("?") ? "&" : "?"}t=${Date.now()}`;
    if (isVideo) {
      return `<div class="muted">Вложение:</div><video controls preload="metadata" style="max-width:100%;max-height:220px;border-radius:8px;background:#111;" src="${escapeHtml(url)}"></video>`;
    }
    return `<div class="muted">Вложение:</div><img src="${escapeHtml(url)}" alt="Вложение" style="display:block;max-width:100%;max-height:220px;border-radius:8px;object-fit:contain;background:#111;">`;
  }

  function renderParticipantAnswers(items, taskOverview) {
    participantAnswers.innerHTML = "";
    const answers = Array.isArray(items) ? items : [];
    const overview = Array.isArray(taskOverview) ? taskOverview : [];

    if (overview.length) {
      const latestByTask = latestAnswersByTask(answers);
      const completedTasks = overview.filter((task) => {
        const answer = latestByTask.get(task.taskId);
        return answer && answer.isCorrect;
      }).length;
      const overviewBlock = document.createElement("div");
      overviewBlock.className = "participant-task-overview";
      overviewBlock.innerHTML = `
        <div class="participant-task-overview-header">
          <div>
            <strong>Статусы по заданиям</strong>
            <div class="muted">Зелёный: пройдено, красный: есть ошибка, жёлтый: нет ответа.</div>
          </div>
          <div class="participant-task-overview-total">${completedTasks}/${overview.length}</div>
        </div>
        ${renderProgressBar(completedTasks, overview.length, false)}
      `;
      const grid = document.createElement("div");
      grid.className = "participant-task-grid";
      overview.forEach((task) => {
        const answer = latestByTask.get(task.taskId);
        const status = renderParticipantTaskStatus(task, answer);
        const card = document.createElement("div");
        card.className = `participant-task-card ${status.className}`;
        card.innerHTML = `
          <div class="participant-task-card-top">
            <strong>${escapeHtml(task.title)}</strong>
            <span class="badge ${status.className === "task-status-ok" ? "badge-ok" : status.className === "task-status-danger" ? "badge-danger" : "badge-warn"}">${status.label}</span>
          </div>
          <div class="muted">${escapeHtml(task.locationTitle)} · ${escapeHtml(TASK_TYPE_LABELS[task.taskType] || task.taskType)} · Баллы: ${task.maxScore}</div>
          <div class="participant-task-card-answer">${answer ? `Последний ответ: ${escapeHtml(answer.submittedAnswer || "—")}` : "Ответа пока нет."}</div>
        `;
        grid.appendChild(card);
      });
      overviewBlock.appendChild(grid);
      participantAnswers.appendChild(overviewBlock);
    }

    if (!answers.length) {
      const empty = document.createElement("p");
      empty.className = "muted";
      empty.textContent = "Ответов пока нет.";
      participantAnswers.appendChild(empty);
      return;
    }

    const title = document.createElement("h4");
    title.textContent = "История ответов";
    participantAnswers.appendChild(title);
    const list = document.createElement("div");
    list.className = "answer-items";
    answers.forEach((row) => {
      const block = document.createElement("div");
      block.className = "answer-item";
      const expected = row.expectedAnswer ? ` · Ожидалось: ${escapeHtml(row.expectedAnswer)}` : "";
      block.innerHTML = `
        <div><strong>${escapeHtml(row.taskTitle)}</strong> <span class="muted">(${escapeHtml(TASK_TYPE_LABELS[row.taskType] || row.taskType)})</span></div>
        <div class="muted">Точка: ${escapeHtml(row.locationTitle)} · ${formatAgo(row.createdAt)}</div>
        <div>Ответ: ${escapeHtml(row.submittedAnswer || "—")}</div>
        <div class="muted">${row.isCorrect ? "Совпадает с ключом" : "Не совпадает с ключом"}${expected}</div>
      `;
      list.appendChild(block);
    });
    participantAnswers.appendChild(list);
  }

  function renderLocationTasks() {
    pointTasksList.innerHTML = "";
    if (!selectedLocation) {
      closeTaskEditor();
      return;
    }
    if (!cachedTasks.length) {
      pointTasksList.innerHTML = '<p class="muted">В этой точке пока нет заданий.</p>';
      return;
    }
    const items = document.createElement("div");
    items.className = "task-items";
    cachedTasks.forEach((task) => {
      const row = document.createElement("div");
      row.className = "task-item";
      row.innerHTML = `
          <div>
          <div><strong>${escapeHtml(task.title)}</strong></div>
          <div class="muted">${escapeHtml(TASK_TYPE_LABELS[task.taskType] || task.taskType)} · Баллы: ${task.maxScore}</div>
          <div class="muted">${escapeHtml(task.description)}</div>
          ${task.options && task.options.length ? `<div class="muted">Варианты: ${escapeHtml(task.options.join(" | "))}</div>` : ""}
          ${renderTaskMediaBlock(task)}
        </div>
        <div class="actions">
          <button class="btn btn-ghost" data-edit-task="${escapeHtml(task.taskId)}">Редактировать</button>
          <button class="btn btn-ghost" data-delete-task="${escapeHtml(task.taskId)}">Удалить</button>
        </div>
      `;
      items.appendChild(row);
    });
    pointTasksList.appendChild(items);

    items.querySelectorAll("button[data-edit-task]").forEach((btn) => {
      btn.addEventListener("click", () => {
        editTask(btn.getAttribute("data-edit-task"));
      });
    });
    items.querySelectorAll("button[data-delete-task]").forEach((btn) => {
      btn.addEventListener("click", () => {
        deleteTask(btn.getAttribute("data-delete-task"));
      });
    });
  }

  function renderLocations() {
    pointsList.innerHTML = "";
    if (!cachedLocations.length) {
      pointsList.innerHTML = '<p class="muted">Точки пока не добавлены.</p>';
      updateSelectedPointCard();
      return;
    }

    cachedLocations.forEach((location) => {
      const button = document.createElement("button");
      const isActive = selectedLocation && selectedLocation.locationId === location.locationId;
      button.className = `point-item${isActive ? " active" : ""}`;
      button.innerHTML = `
        <div><strong>${location.position}. ${escapeHtml(location.title)}</strong></div>
        <div class="muted">QR: <code>${escapeHtml(location.qrCode)}</code> · Заданий: ${location.tasksCount || 0}</div>
      `;
      button.addEventListener("click", () => {
        selectedLocation = location;
        renderLocations();
        updateSelectedPointCard();
        loadLocationTasks(location.locationId);
      });
      pointsList.appendChild(button);
    });

    updateSelectedPointCard();
  }

  function updateSelectedQuestCard() {
    const editBtn = el("btnEditQuest");
    const deleteBtn = el("btnDeleteQuest");
    const exportBtn = el("btnExportQuest");
    const inviteBtn = el("btnInviteTeacher");
    const questIdInput = el("locationQuestId");
    if (!selectedQuestId) {
      selectedQuestCard.textContent = "Выберите квест из списка.";
      editBtn.disabled = true;
      deleteBtn.disabled = true;
      exportBtn.disabled = true;
      inviteBtn.disabled = true;
      questIdInput.value = "";
      closeQuestEditor();
      closeTaskEditor();
      return;
    }
    const quest = cachedTeacherQuests.find((item) => item.questId === selectedQuestId);
    if (!quest) {
      selectedQuestCard.textContent = "Квест не найден.";
      editBtn.disabled = true;
      deleteBtn.disabled = true;
      exportBtn.disabled = true;
      inviteBtn.disabled = true;
      return;
    }
    selectedQuestCard.innerHTML = `
      <p><strong>${escapeHtml(quest.title)}</strong></p>
      <p>${escapeHtml(quest.description)}</p>
      <p class="muted">Вуз: ${escapeHtml(quest.institutionName)} · Статус: ${quest.isActive ? "активен" : "скрыт"}</p>
      <p class="muted">Владелец: ${escapeHtml(quest.ownerName || "неизвестно")}</p>
    `;
    editBtn.disabled = false;
    deleteBtn.disabled = false;
    exportBtn.disabled = false;
    inviteBtn.disabled = false;
    questIdInput.value = quest.questId;
  }

  function renderTeacherQuests() {
    questsList.innerHTML = "";
    if (!cachedTeacherQuests.length) {
      questsList.innerHTML = '<p class="muted">Пока нет доступных квестов.</p>';
      selectedQuestId = null;
      updateSelectedQuestCard();
      return;
    }
    if (!selectedQuestId || !cachedTeacherQuests.some((quest) => quest.questId === selectedQuestId)) {
      selectedQuestId = cachedTeacherQuests[0].questId;
    }
    cachedTeacherQuests.forEach((quest) => {
      const button = document.createElement("button");
      const active = selectedQuestId === quest.questId;
      button.className = `quest-item${active ? " active" : ""}`;
      button.innerHTML = `
        <div><strong>${escapeHtml(quest.title)}</strong></div>
        <div class="muted">${escapeHtml(quest.institutionName)}</div>
        <div class="muted">${quest.isActive ? "Активен" : "Скрыт"} · ${escapeHtml(quest.ownerName || "без владельца")}</div>
      `;
      button.addEventListener("click", async () => {
        selectedQuestId = quest.questId;
        renderTeacherQuests();
        await loadQuestLocations(selectedQuestId);
        await loadQuestTeachers(selectedQuestId);
      });
      questsList.appendChild(button);
    });
    updateSelectedQuestCard();
  }

  function renderQuestTeachers() {
    questTeachersList.innerHTML = "";
    if (!selectedQuestId) {
      questTeachersList.innerHTML = '<p class="muted">Сначала выберите квест.</p>';
      return;
    }
    if (!cachedQuestTeachers.length) {
      questTeachersList.innerHTML = '<p class="muted">В этом квесте пока только владелец.</p>';
      return;
    }
    cachedQuestTeachers.forEach((teacher) => {
      const row = document.createElement("div");
      row.className = "teacher-item";
      row.innerHTML = `
        <div>
          <strong>${escapeHtml(teacher.teacherName)}</strong>
          <span class="muted">${teacher.isOwner ? " (владелец)" : ""}</span>
        </div>
        <div class="actions">
          <button class="btn btn-ghost" data-remove-teacher="${escapeHtml(teacher.teacherId)}" ${teacher.isOwner ? "disabled" : ""}>
            Удалить
          </button>
        </div>
      `;
      questTeachersList.appendChild(row);
    });
    questTeachersList.querySelectorAll("button[data-remove-teacher]").forEach((btn) => {
      btn.addEventListener("click", () => removeTeacherFromQuest(btn.getAttribute("data-remove-teacher")));
    });
  }

  async function getQuestLocationNames(questId) {
    if (!questId) return {};
    if (questLocationNamesCache[questId]) return questLocationNamesCache[questId];
    const data = await api(`/teacher/quests/${encodeURIComponent(questId)}/locations`);
    const map = {};
    (Array.isArray(data) ? data : []).forEach((location) => {
      map[location.locationId] = location.title;
    });
    questLocationNamesCache[questId] = map;
    return map;
  }

  async function loadTeacherQuests() {
    try {
      const data = await api("/teacher/quests");
      cachedTeacherQuests = Array.isArray(data) ? data : [];
      renderTeacherQuests();
      if (selectedQuestId) {
        await loadQuestLocations(selectedQuestId);
        await loadQuestTeachers(selectedQuestId);
      } else {
        cachedLocations = [];
        selectedLocation = null;
        renderLocations();
        cachedTasks = [];
        renderLocationTasks();
        cachedQuestTeachers = [];
        renderQuestTeachers();
      }
    } catch (e) {
      cachedTeacherQuests = [];
      selectedQuestId = null;
      questsList.innerHTML = `<p class="muted">${escapeHtml(e.message)}</p>`;
      selectedQuestCard.textContent = "Не удалось загрузить квесты.";
      questTeachersList.innerHTML = "";
    }
  }

  async function loadQuestTeachers(questId) {
    if (!questId) {
      cachedQuestTeachers = [];
      renderQuestTeachers();
      return;
    }
    try {
      const data = await api(`/teacher/quests/${encodeURIComponent(questId)}/teachers`);
      cachedQuestTeachers = Array.isArray(data) ? data : [];
      renderQuestTeachers();
    } catch (e) {
      cachedQuestTeachers = [];
      questTeachersList.innerHTML = `<p class="muted">${escapeHtml(e.message)}</p>`;
    }
  }

  async function editSelectedQuest() {
    teacherManageMessage.textContent = "";
    if (!selectedQuestId) return;
    const quest = cachedTeacherQuests.find((item) => item.questId === selectedQuestId);
    if (!quest) return;
    openQuestEditor(quest);
  }

  async function saveQuestEdit() {
    const questId = selectedQuestId;
    if (!questId) return;
    const title = el("editQuestTitle").value.trim();
    const description = el("editQuestDescription").value.trim();
    const institutionName = el("editQuestInstitution").value.trim();
    const isActive = el("editQuestIsActive").checked;
    if (!title || !description || !institutionName) {
      el("questEditMessage").textContent = "Заполните все поля квеста.";
      return;
    }
    try {
      await api(`/teacher/quests/${encodeURIComponent(questId)}`, {
        method: "PUT",
        body: JSON.stringify({
          title,
          description,
          institutionName,
          isActive
        })
      });
      await loadTeacherQuests();
      closeQuestEditor();
      teacherManageMessage.textContent = "Квест обновлен.";
    } catch (e) {
      el("questEditMessage").textContent = e.message;
    }
  }

  async function deleteSelectedQuest() {
    teacherManageMessage.textContent = "";
    if (!selectedQuestId) return;
    if (!confirm("Удалить выбранный квест и все его точки/задания?")) return;
    try {
      await api(`/teacher/quests/${encodeURIComponent(selectedQuestId)}`, { method: "DELETE" });
      selectedQuestId = null;
      selectedLocation = null;
      await loadTeacherQuests();
      teacherManageMessage.textContent = "Квест удален.";
    } catch (e) {
      teacherManageMessage.textContent = e.message;
    }
  }

  async function exportSelectedQuest() {
    teacherManageMessage.textContent = "";
    if (!selectedQuestId) {
      teacherManageMessage.textContent = "Сначала выберите квест.";
      return;
    }
    try {
      const quest = cachedTeacherQuests.find((item) => item.questId === selectedQuestId);
      if (!quest) {
        teacherManageMessage.textContent = "Квест не найден.";
        return;
      }
      const locations = await api(`/teacher/quests/${encodeURIComponent(selectedQuestId)}/locations`);
      const preparedLocations = [];
      for (const location of (Array.isArray(locations) ? locations : [])) {
        const tasks = await api(`/teacher/locations/${encodeURIComponent(location.locationId)}/tasks`);
        preparedLocations.push({
          position: location.position,
          title: location.title,
          qrCode: location.qrCode,
          tasks: Array.isArray(tasks) ? tasks.map((task) => ({
            title: task.title,
            description: task.description,
            taskType: task.taskType,
            maxScore: task.maxScore,
            options: task.options || [],
            correctOptionIndex: task.correctOptionIndex ?? null,
            correctAnswer: task.correctAnswer ?? null,
            mediaUrl: task.mediaUrl ?? null,
            mediaType: task.mediaType ?? null
          })) : []
        });
      }
      const payload = {
        schema: "abitour.teacher.quest.v1",
        exportedAt: new Date().toISOString(),
        quest: {
          title: quest.title,
          description: quest.description,
          institutionName: quest.institutionName,
          isActive: !!quest.isActive
        },
        locations: preparedLocations
      };
      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `quest-export-${selectedQuestId}.json`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      teacherManageMessage.textContent = "Экспорт квеста готов.";
    } catch (e) {
      teacherManageMessage.textContent = e.message;
    }
  }

  async function importQuestFromFile(file) {
    teacherManageMessage.textContent = "";
    if (!file) return;
    try {
      const raw = await file.text();
      const parsed = JSON.parse(raw);
      if (!parsed || !parsed.quest || !Array.isArray(parsed.locations)) {
        throw new Error("Некорректный формат файла импорта.");
      }
      const createdQuest = await api("/teacher/quests", {
        method: "POST",
        body: JSON.stringify({
          title: String(parsed.quest.title || "").trim(),
          description: String(parsed.quest.description || "").trim(),
          institutionName: String(parsed.quest.institutionName || "").trim(),
          isActive: !!parsed.quest.isActive
        })
      });
      const newQuestId = createdQuest.questId;
      const sortedLocations = [...parsed.locations]
        .filter((item) => item && String(item.title || "").trim())
        .sort((a, b) => Number(a.position || 0) - Number(b.position || 0));
      for (let i = 0; i < sortedLocations.length; i += 1) {
        const item = sortedLocations[i];
        const createdLocation = await api(`/teacher/quests/${encodeURIComponent(newQuestId)}/locations`, {
          method: "POST",
          body: JSON.stringify({
            position: Number(item.position || i + 1),
            title: String(item.title || "").trim(),
            qrCode: String(item.qrCode || generateQrId()).trim()
          })
        });
        const tasks = Array.isArray(item.tasks) ? item.tasks : [];
        for (const task of tasks) {
          await api(`/teacher/locations/${encodeURIComponent(createdLocation.locationId)}/tasks`, {
            method: "POST",
            body: JSON.stringify({
              title: String(task.title || "").trim(),
              description: String(task.description || "").trim(),
              taskType: String(task.taskType || "INFO").toUpperCase(),
              maxScore: Number(task.maxScore || 1),
              options: Array.isArray(task.options) ? task.options.map((opt) => String(opt || "").trim()).filter(Boolean) : [],
              correctOptionIndex: task.correctOptionIndex == null ? null : Number(task.correctOptionIndex),
              correctAnswer: task.correctAnswer == null ? null : String(task.correctAnswer).trim(),
              mediaUrl: task.mediaUrl == null ? null : String(task.mediaUrl).trim(),
              mediaType: task.mediaType == null ? null : String(task.mediaType).trim().toUpperCase()
            })
          });
        }
      }
      selectedQuestId = newQuestId;
      await loadTeacherQuests();
      await loadQuestLocations(newQuestId);
      await refreshProgress();
      teacherManageMessage.textContent = "Квест импортирован из файла.";
    } catch (e) {
      teacherManageMessage.textContent = e.message || "Ошибка импорта квеста.";
    }
  }

  async function inviteTeacherToQuest() {
    teacherManageMessage.textContent = "";
    if (!selectedQuestId) {
      teacherManageMessage.textContent = "Сначала выберите квест.";
      return;
    }
    const teacherName = el("inviteTeacherName").value.trim();
    if (!teacherName) {
      teacherManageMessage.textContent = "Введите имя преподавателя.";
      return;
    }
    try {
      await api(`/teacher/quests/${encodeURIComponent(selectedQuestId)}/teachers/invite`, {
        method: "POST",
        body: JSON.stringify({ teacherName })
      });
      el("inviteTeacherName").value = "";
      await loadQuestTeachers(selectedQuestId);
      teacherManageMessage.textContent = "Преподаватель приглашен.";
    } catch (e) {
      teacherManageMessage.textContent = e.message;
    }
  }

  async function removeTeacherFromQuest(teacherId) {
    teacherManageMessage.textContent = "";
    if (!selectedQuestId || !teacherId) return;
    if (!confirm("Удалить преподавателя из этого квеста?")) return;
    try {
      await api(`/teacher/quests/${encodeURIComponent(selectedQuestId)}/teachers/${encodeURIComponent(teacherId)}`, {
        method: "DELETE"
      });
      await loadQuestTeachers(selectedQuestId);
      teacherManageMessage.textContent = "Преподаватель удален.";
    } catch (e) {
      teacherManageMessage.textContent = e.message;
    }
  }

  async function loadQuestLocations(questId, autoSelectLocationId) {
    if (!questId) {
      cachedLocations = [];
      selectedLocation = null;
      renderLocations();
      return;
    }

    locationMessage.textContent = "";
    try {
      const data = await api(`/teacher/quests/${encodeURIComponent(questId)}/locations`);
      cachedLocations = Array.isArray(data) ? data : [];
      questLocationNamesCache[questId] = Object.fromEntries(
        cachedLocations.map((loc) => [loc.locationId, loc.title])
      );
      if (autoSelectLocationId) {
        selectedLocation = cachedLocations.find((loc) => loc.locationId === autoSelectLocationId) || null;
      } else if (selectedLocation) {
        selectedLocation = cachedLocations.find((loc) => loc.locationId === selectedLocation.locationId) || null;
      }
      if (!selectedLocation && cachedLocations.length) {
        selectedLocation = cachedLocations[0];
      }
      renderLocations();
      el("locationPosition").value = String(nextLocationPosition());
      if (selectedLocation) {
        await loadLocationTasks(selectedLocation.locationId);
      } else {
        cachedTasks = [];
        renderLocationTasks();
      }
    } catch (e) {
      cachedLocations = [];
      selectedLocation = null;
      pointsList.innerHTML = `<p class="muted">${escapeHtml(e.message)}</p>`;
      updateSelectedPointCard();
      cachedTasks = [];
      renderLocationTasks();
    }
  }

  async function loadLocationTasks(locationId) {
    if (!locationId) {
      cachedTasks = [];
      renderLocationTasks();
      return;
    }
    try {
      const data = await api(`/teacher/locations/${encodeURIComponent(locationId)}/tasks`);
      cachedTasks = Array.isArray(data) ? data : [];
      renderLocationTasks();
    } catch (e) {
      cachedTasks = [];
      pointTasksList.innerHTML = `<p class="muted">${escapeHtml(e.message)}</p>`;
    }
  }

  async function authenticate(mode) {
    authMessage.textContent = "";
    try {
      const name = authName.value.trim();
      const password = authPassword.value.trim();
      if (!name || !password) {
        authMessage.textContent = "Введите имя и пароль.";
        return;
      }

      const endpoint = mode === "register" ? "/auth/register" : "/auth/login";
      const payload = mode === "register"
        ? { name, password, role: "TEACHER" }
        : { name, password };

      const result = await api(endpoint, {
        method: "POST",
        body: JSON.stringify(payload)
      });

      if (!result || !result.user || result.user.role !== "TEACHER") {
        authMessage.textContent = "Этот аккаунт не имеет роли преподавателя.";
        return;
      }

      token = result.token;
      localStorage.setItem("teacher.token", token);
      setAuthState(true);
      authMessage.textContent = mode === "register" ? "Аккаунт создан." : "";
      await refreshProgress();
      await loadTeacherQuests();
    } catch (e) {
      authMessage.textContent = e.message || (mode === "register" ? "Ошибка регистрации" : "Ошибка входа");
    }
  }

  async function refreshProgress() {
    try {
      const list = await api("/teacher/progress");
      tbody.innerHTML = "";
      list.forEach((row) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
          <td>${escapeHtml(row.participantName)}</td>
          <td>${escapeHtml(row.questId)}</td>
          <td>${renderProgressBar(row.completedLocations, row.totalLocations, true)}</td>
          <td>${row.score}</td>
          <td>${row.attemptsCount}</td>
          <td>${row.wrongAnswersCount}</td>
          <td>${formatAgo(row.lastActivityAt)}</td>
          <td>${escapeHtml(currentLocationLabel(row.currentLocationTitle))}</td>
          <td><button class="btn btn-ghost" data-user="${escapeHtml(row.participantId)}" data-quest="${escapeHtml(row.questId)}">Открыть</button></td>
        `;
        tbody.appendChild(tr);
      });
      tbody.querySelectorAll("button[data-user]").forEach((btn) => {
        btn.addEventListener("click", async () => {
          selectedParticipantId = btn.getAttribute("data-user");
          selectedParticipantQuestId = btn.getAttribute("data-quest");
          el("btnSendHint").disabled = false;
          el("btnSendRecommendation").disabled = false;
          await loadParticipantDetails();
        });
      });
    } catch (e) {
      tbody.innerHTML = `<tr><td colspan="9">${escapeHtml(e.message)}</td></tr>`;
    }
  }

  async function loadParticipantDetails() {
    if (!selectedParticipantId || !selectedParticipantQuestId) return;
    try {
      const data = await api(`/teacher/participants/${encodeURIComponent(selectedParticipantId)}?questId=${encodeURIComponent(selectedParticipantQuestId)}`);
      const taskOverview = await getQuestTaskOverview(selectedParticipantQuestId);
      let locationNamesMap = {};
      try {
        locationNamesMap = await getQuestLocationNames(selectedParticipantQuestId);
      } catch (_e) {
        locationNamesMap = {};
      }
      const locations = data.completedLocationIds.length
        ? data.completedLocationIds.map((id) => locationNamesMap[id] || `Точка (${id})`).join(", ")
        : "нет";
      participantCard.innerHTML = `
        <div class="participant-summary-header">
          <div>
            <p><strong>Участник:</strong> ${escapeHtml(data.participantName)}</p>
            <p><strong>Квест:</strong> ${escapeHtml(data.questId)}</p>
          </div>
          <div class="participant-current-location">
            <span class="muted">Текущая точка</span>
            <strong>${escapeHtml(currentLocationLabel(data.currentLocationTitle))}</strong>
          </div>
        </div>
        ${renderProgressBar(data.completedLocations, data.totalLocations, false)}
        <div class="participant-summary-grid">
          <div class="participant-stat"><span class="muted">Баллы</span><strong>${data.score}</strong></div>
          <div class="participant-stat"><span class="muted">Попытки</span><strong>${data.attemptsCount}</strong></div>
          <div class="participant-stat"><span class="muted">Ошибки</span><strong>${data.wrongAnswersCount}</strong></div>
          <div class="participant-stat"><span class="muted">Активность</span><strong>${formatAgo(data.lastActivityAt)}</strong></div>
        </div>
        <p><strong>Пройденные точки:</strong> ${escapeHtml(locations)}</p>
        <p><strong>Всего заданий в квесте:</strong> ${taskOverview.length}</p>
      `;
      await loadParticipantAnswers(taskOverview);
    } catch (e) {
      participantCard.textContent = e.message;
      participantAnswers.textContent = e.message;
    }
  }

  async function loadParticipantAnswers(taskOverview) {
    if (!selectedParticipantId || !selectedParticipantQuestId) return;
    try {
      const list = await api(`/teacher/participants/${encodeURIComponent(selectedParticipantId)}/answers?questId=${encodeURIComponent(selectedParticipantQuestId)}`);
      renderParticipantAnswers(Array.isArray(list) ? list : [], taskOverview || []);
    } catch (e) {
      participantAnswers.innerHTML = `<p class="muted">${escapeHtml(e.message)}</p>`;
    }
  }

  async function sendHint() {
    hintMessage.textContent = "";
    if (!selectedParticipantId) {
      hintMessage.textContent = "Сначала выберите участника.";
      return;
    }
    try {
      const hint = el("hintText").value.trim();
      if (!hint) {
        hintMessage.textContent = "Введите текст подсказки.";
        return;
      }
      await api("/teacher/hint", {
        method: "POST",
        body: JSON.stringify({ participantId: selectedParticipantId, hint })
      });
      hintMessage.textContent = "Подсказка отправлена.";
      el("hintText").value = "";
    } catch (e) {
      hintMessage.textContent = e.message;
    }
  }

  async function sendRecommendation() {
    recommendationMessage.textContent = "";
    if (!selectedParticipantId || !selectedParticipantQuestId) {
      recommendationMessage.textContent = "Сначала выберите участника.";
      return;
    }
    try {
      const recommendation = el("recommendationText").value.trim();
      if (!recommendation) {
        recommendationMessage.textContent = "Введите текст рекомендации.";
        return;
      }
      await api("/teacher/recommendation", {
        method: "POST",
        body: JSON.stringify({
          participantId: selectedParticipantId,
          questId: selectedParticipantQuestId,
          recommendation
        })
      });
      recommendationMessage.textContent = "Рекомендация отправлена.";
      el("recommendationText").value = "";
    } catch (e) {
      recommendationMessage.textContent = e.message;
    }
  }

  async function createQuest() {
    questMessage.textContent = "";
    try {
      const payload = {
        title: el("questTitle").value.trim(),
        description: el("questDescription").value.trim(),
        institutionName: el("questInstitution").value.trim(),
        isActive: el("questIsActive").checked
      };
      const data = await api("/teacher/quests", { method: "POST", body: JSON.stringify(payload) });
      questMessage.textContent = `Квест создан: ${data.questId}`;
      selectedQuestId = data.questId;
      await loadTeacherQuests();
      await refreshProgress();
    } catch (e) {
      questMessage.textContent = e.message;
    }
  }

  async function createLocation() {
    locationMessage.textContent = "";
    try {
      const questId = selectedQuestId || el("locationQuestId").value.trim();
      if (!questId) {
        locationMessage.textContent = "Выберите квест.";
        return;
      }
      selectedQuestId = questId;
      const payload = {
        position: Number(el("locationPosition").value || "1"),
        title: el("locationTitle").value.trim(),
        qrCode: generateQrId()
      };
      if (!payload.title) {
        locationMessage.textContent = "Введите название точки.";
        return;
      }
      const data = await api(`/teacher/quests/${encodeURIComponent(questId)}/locations`, {
        method: "POST",
        body: JSON.stringify(payload)
      });
      locationMessage.textContent = `Точка создана: ${data.locationId}, QR-ID: ${data.qrCode}`;
      el("locationTitle").value = "";
      await loadQuestLocations(questId, data.locationId);
      el("locationPosition").value = String(nextLocationPosition());
    } catch (e) {
      locationMessage.textContent = e.message;
    }
  }

  async function createTask() {
    taskMessage.textContent = "";
    try {
      if (!selectedLocation) {
        taskMessage.textContent = "Выберите точку из списка.";
        return;
      }
      const currentTaskType = taskType();
      const normalizedQuizOptions = quizOptions
        .map((option) => option.text.trim())
        .filter(Boolean);
      const correctOptionIndex = quizOptions.findIndex((option) => option.isCorrect);
      const payload = {
        title: el("taskTitle").value.trim(),
        description: el("taskDescription").value.trim(),
        taskType: currentTaskType,
        maxScore: Number(el("taskMaxScore").value || "1"),
        options: currentTaskType === "QUIZ" ? normalizedQuizOptions : [],
        correctOptionIndex: currentTaskType === "QUIZ" ? correctOptionIndex : null,
        correctAnswer: currentTaskType === "QUESTION" ? (el("taskCorrectAnswer").value.trim() || null) : null,
        mediaUrl: el("taskMediaUrl").value.trim() || null,
        mediaType: el("taskMediaType").value || null
      };

      if (currentTaskType === "QUIZ" && payload.options.length < 2) {
        taskMessage.textContent = "Для викторины добавьте минимум 2 варианта.";
        return;
      }
      if (currentTaskType === "QUIZ" && (payload.correctOptionIndex == null || payload.correctOptionIndex < 0)) {
        taskMessage.textContent = "Выберите правильный вариант.";
        return;
      }

      const data = await api(`/teacher/locations/${encodeURIComponent(selectedLocation.locationId)}/tasks`, {
        method: "POST",
        body: JSON.stringify(payload)
      });
      taskMessage.textContent = `Задание создано: ${data.taskId}`;
      el("taskTitle").value = "";
      el("taskDescription").value = "";
      el("taskCorrectAnswer").value = "";
      quizOptions = [];
      renderQuizOptions();
      el("taskMediaUrl").value = "";
      el("taskMediaType").value = "";
      el("taskMediaFile").value = "";
      await loadQuestLocations(selectedQuestId || el("locationQuestId").value.trim(), selectedLocation.locationId);
      await loadLocationTasks(selectedLocation.locationId);
    } catch (e) {
      taskMessage.textContent = e.message;
    }
  }

  async function editTask(taskId) {
    const current = cachedTasks.find((task) => task.taskId === taskId);
    if (!current) return;
    openTaskEditor(current);
  }

  async function saveTaskEdit() {
    const taskId = el("editTaskId").value.trim();
    if (!taskId) return;
    const currentTaskType = el("editTaskType").value;
    const normalizedOptions = editQuizOptions
      .map((option) => option.text.trim())
      .filter(Boolean);
    const correctOptionIndex = editQuizOptions.findIndex((option) => option.isCorrect);
    if (currentTaskType === "QUIZ" && normalizedOptions.length < 2) {
      el("taskEditMessage").textContent = "Для викторины добавьте минимум 2 варианта.";
      return;
    }
    if (currentTaskType === "QUIZ" && correctOptionIndex < 0) {
      el("taskEditMessage").textContent = "Выберите правильный вариант.";
      return;
    }
    if (currentTaskType === "QUESTION" && !el("editTaskCorrectAnswer").value.trim()) {
      el("taskEditMessage").textContent = "Для типа \"Ответ текстом\" нужен правильный ответ.";
      return;
    }
    try {
      await api(`/teacher/tasks/${encodeURIComponent(taskId)}`, {
        method: "PUT",
        body: JSON.stringify({
          title: el("editTaskTitle").value.trim(),
          description: el("editTaskDescription").value.trim(),
          taskType: currentTaskType,
          maxScore: Number(el("editTaskMaxScore").value || "1"),
          options: currentTaskType === "QUIZ" ? normalizedOptions : [],
          correctOptionIndex: currentTaskType === "QUIZ" ? correctOptionIndex : null,
          correctAnswer: currentTaskType === "QUESTION" ? (el("editTaskCorrectAnswer").value.trim() || null) : null,
          mediaUrl: el("editTaskMediaUrl").value.trim() || null,
          mediaType: el("editTaskMediaType").value || null
        })
      });
      closeTaskEditor();
      await loadLocationTasks(selectedLocation.locationId);
      await loadQuestLocations(selectedQuestId || el("locationQuestId").value.trim(), selectedLocation.locationId);
      taskMessage.textContent = "Задание обновлено.";
    } catch (e) {
      el("taskEditMessage").textContent = e.message;
    }
  }

  async function deleteTask(taskId) {
    const confirmed = confirm("Удалить это задание?");
    if (!confirmed) return;
    try {
      await api(`/teacher/tasks/${encodeURIComponent(taskId)}`, { method: "DELETE" });
      await loadLocationTasks(selectedLocation.locationId);
      await loadQuestLocations(selectedQuestId || el("locationQuestId").value.trim(), selectedLocation.locationId);
    } catch (e) {
      taskMessage.textContent = e.message;
    }
  }

  async function downloadQrPrint() {
    taskMessage.textContent = "";
    if (!selectedLocation) return;
    try {
      const response = await fetch(
        `${apiBase}/teacher/locations/${encodeURIComponent(selectedLocation.locationId)}/qr-print.pdf`,
        {
          method: "GET",
          headers: token ? { Authorization: `Bearer ${token}` } : {}
        }
      );
      if (!response.ok) {
        const body = await response.text();
        let msg = `HTTP ${response.status}`;
        try {
          const parsed = body ? JSON.parse(body) : null;
          if (parsed && parsed.message) msg = parsed.message;
        } catch (_e) {}
        throw new Error(msg);
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      const safeTitle = selectedLocation.title.replace(/[^a-zA-Z0-9а-яА-Я_-]+/g, "_");
      a.href = url;
      a.download = `qr-print-${safeTitle || "point"}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      taskMessage.textContent = e.message || "Не удалось скачать PDF.";
    }
  }

  function logout() {
    token = "";
    selectedParticipantId = null;
    selectedParticipantQuestId = null;
    selectedQuestId = null;
    selectedLocation = null;
    cachedTeacherQuests = [];
    cachedQuestTeachers = [];
    cachedLocations = [];
    cachedTasks = [];
    questLocationNamesCache = {};
    localStorage.removeItem("teacher.token");
    setAuthState(false);
    tbody.innerHTML = "";
    participantCard.textContent = "Выберите участника в таблице.";
    questsList.innerHTML = "";
    selectedQuestCard.textContent = "Выберите квест из списка.";
    questTeachersList.innerHTML = "";
    renderLocations();
    renderLocationTasks();
    participantAnswers.textContent = "Ответы появятся после выбора участника.";
    recommendationMessage.textContent = "";
    closeQuestEditor();
    closeTaskEditor();
    el("btnSendHint").disabled = true;
    el("btnSendRecommendation").disabled = true;
  }

  async function uploadMediaToFields({ inputId, urlId, typeId, messageElement }) {
    messageElement.textContent = "";
    try {
      const input = el(inputId);
      const file = input.files && input.files[0];
      if (!file) {
        messageElement.textContent = "Выберите файл для загрузки.";
        return;
      }
      const isImage = file.type.startsWith("image/");
      const isVideo = file.type.startsWith("video/");
      if (!isImage && !isVideo) {
        messageElement.textContent = "Поддерживаются только изображения и видео.";
        return;
      }

      const formData = new FormData();
      formData.append("file", file);
      const uploaded = await api("/teacher/media/upload", {
        method: "POST",
        body: formData
      });

      el(urlId).value = uploaded.url || "";
      el(typeId).value = uploaded.mediaType || (isImage ? "IMAGE" : "VIDEO");
      input.value = "";
      messageElement.textContent = "Файл загружен на сервер и прикреплен к заданию.";
    } catch (e) {
      messageElement.textContent = e.message;
    }
  }

  async function uploadMedia() {
    await uploadMediaToFields({
      inputId: "taskMediaFile",
      urlId: "taskMediaUrl",
      typeId: "taskMediaType",
      messageElement: taskMessage
    });
  }

  async function uploadEditMedia() {
    await uploadMediaToFields({
      inputId: "editTaskMediaFile",
      urlId: "editTaskMediaUrl",
      typeId: "editTaskMediaType",
      messageElement: el("taskEditMessage")
    });
  }

  el("btnLogin").addEventListener("click", () => authenticate("login"));
  el("btnRegister").addEventListener("click", () => authenticate("register"));
  el("btnRefresh").addEventListener("click", () => {
    refreshProgress();
    loadTeacherQuests();
  });
  el("btnEditQuest").addEventListener("click", editSelectedQuest);
  el("btnSaveQuestEdit").addEventListener("click", saveQuestEdit);
  el("btnCancelQuestEdit").addEventListener("click", closeQuestEditor);
  el("btnDeleteQuest").addEventListener("click", deleteSelectedQuest);
  el("btnExportQuest").addEventListener("click", exportSelectedQuest);
  el("btnImportQuest").addEventListener("click", () => el("importQuestFile").click());
  el("importQuestFile").addEventListener("change", async (e) => {
    const file = e.target.files && e.target.files[0];
    await importQuestFromFile(file);
    e.target.value = "";
  });
  el("btnInviteTeacher").addEventListener("click", inviteTeacherToQuest);
  el("btnSendHint").addEventListener("click", sendHint);
  el("btnSendRecommendation").addEventListener("click", sendRecommendation);
  el("btnCreateQuest").addEventListener("click", createQuest);
  el("btnCreateLocation").addEventListener("click", createLocation);
  el("btnCreateTask").addEventListener("click", createTask);
  el("btnAddQuizOption").addEventListener("click", () => addQuizOption(""));
  el("taskType").addEventListener("change", toggleTaskTypeFields);
  el("btnEditAddQuizOption").addEventListener("click", () => {
    editQuizOptions.push({
      id: `edit-opt-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      text: "",
      isCorrect: editQuizOptions.length === 0
    });
    renderEditQuizOptions();
  });
  el("editTaskType").addEventListener("change", toggleEditTaskTypeFields);
  el("btnSaveTaskEdit").addEventListener("click", saveTaskEdit);
  el("btnCancelTaskEdit").addEventListener("click", closeTaskEditor);
  el("btnUploadMedia").addEventListener("click", uploadMedia);
  el("btnEditUploadMedia").addEventListener("click", uploadEditMedia);
  el("btnDownloadQrPrint").addEventListener("click", downloadQrPrint);
  el("btnLogout").addEventListener("click", logout);
  el("btnTheme").addEventListener("click", toggleTheme);
  tabButtons.forEach((button) => {
    button.addEventListener("click", () => {
      setActiveTab(button.getAttribute("data-tab-target"));
    });
  });

  const savedTheme = localStorage.getItem("teacher.theme") || "light";
  applyTheme(savedTheme);
  const savedTab = localStorage.getItem("teacher.activeTab");
  const initialTab = tabPanels.some((panel) => panel.id === savedTab) ? savedTab : "participantsTab";
  setActiveTab(initialTab);

  if (token) {
    setAuthState(true);
    refreshProgress().catch(logout);
    loadTeacherQuests().catch(logout);
  } else {
    setAuthState(false);
  }

  renderLocations();
  renderLocationTasks();
  renderTeacherQuests();
  renderQuestTeachers();
  toggleTaskTypeFields();
  toggleEditTaskTypeFields();
  renderEditQuizOptions();
  addQuizOption("");
  addQuizOption("");
  el("taskType").querySelectorAll("option").forEach((opt) => {
    if (TASK_TYPE_LABELS[opt.value]) {
      opt.textContent = TASK_TYPE_LABELS[opt.value];
    }
  });
  el("editTaskType").querySelectorAll("option").forEach((opt) => {
    if (TASK_TYPE_LABELS[opt.value]) {
      opt.textContent = TASK_TYPE_LABELS[opt.value];
    }
  });
})();
