const api = '/api/v1';

const elements = {
  health: document.querySelector('#health'),
  userSelect: document.querySelector('#user-select'),
  reloadButton: document.querySelector('#reload-button'),
  resetButton: document.querySelector('#reset-button'),
  message: document.querySelector('#message'),
  userSummary: document.querySelector('#user-summary'),
  preferenceSummary: document.querySelector('#preference-summary'),
  pipelineSummary: document.querySelector('#pipeline-summary'),
  historyList: document.querySelector('#history-list'),
  recommendationList: document.querySelector('#recommendation-list'),
};

const scenarioLabels = {
  COLD_START: 'Người dùng mới',
  LIGHT: 'Ít lịch sử',
  HEAVY: 'Đủ lịch sử',
};

const methodLabels = {
  FAISS_COLD_START: 'FAISS theo vị trí',
  FAISS: 'FAISS theo hồ sơ',
  FAISS_LIGHTGBM: 'FAISS + LightGBM',
};

function formatPrice(value) {
  if (!value) return 'Chưa xác định';
  return `${new Intl.NumberFormat('vi-VN').format(value)} đ/tháng`;
}

function formatDate(value) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

function setMessage(text = '', isError = false) {
  elements.message.textContent = text;
  elements.message.classList.toggle('error', isError);
}

async function request(path, options) {
  const response = await fetch(`${api}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.detail || `HTTP ${response.status}`);
  }
  return response.json();
}

function renderDefinitionList(target, entries) {
  target.replaceChildren();
  entries.forEach(([label, value]) => {
    const term = document.createElement('dt');
    const description = document.createElement('dd');
    term.textContent = label;
    description.textContent = value ?? '—';
    target.append(term, description);
  });
}

function renderSummaries(data) {
  renderDefinitionList(elements.userSummary, [
    ['Hồ sơ', data.user.display_name || data.user.email],
    ['Kịch bản', scenarioLabels[data.scenario]],
    ['Thành phố', data.user.city],
    ['Quận/Huyện', data.user.district],
    ['Số lượt xem', String(data.view_count)],
  ]);

  renderDefinitionList(elements.preferenceSummary, [
    ['Giá trung bình', data.profile.average_price ? formatPrice(data.profile.average_price) : 'Chưa có'],
    ['Diện tích', data.profile.average_area ? `${data.profile.average_area} m²` : 'Chưa có'],
    ['Khu vực ưu tiên', data.profile.preferred_district || data.profile.preferred_city],
    ['Loại phòng', data.profile.preferred_room_type || 'Chưa có'],
    ['Tiện ích', data.profile.preferred_amenities.join(', ') || 'Chưa có'],
  ]);

  renderDefinitionList(elements.pipelineSummary, [
    ['Phương pháp', methodLabels[data.ranking_method] || data.ranking_method],
    ['Số kết quả', String(data.items.length)],
    ['Thời gian xử lý', `${data.processing_time_ms} ms`],
    ['Cold start', data.scenario === 'COLD_START' ? 'Có' : 'Không'],
  ]);
}

function renderHistory(events) {
  elements.historyList.replaceChildren();
  if (!events.length) {
    const empty = document.createElement('p');
    empty.className = 'empty';
    empty.textContent = 'Người dùng chưa có lịch sử xem.';
    elements.historyList.append(empty);
    return;
  }

  events.forEach((event) => {
    const row = document.createElement('div');
    row.className = 'history-row';
    const values = [
      [event.room.title, 'history-title'],
      [formatPrice(event.room.price), ''],
      [`${event.room.area} m² · ${event.room.district}`, 'muted'],
      [formatDate(event.viewed_at), 'muted'],
    ];
    values.forEach(([text, className]) => {
      const span = document.createElement('span');
      span.textContent = text;
      if (className) span.className = className;
      row.append(span);
    });
    elements.historyList.append(row);
  });
}

function createRoomCard(item) {
  const card = document.createElement('article');
  card.className = 'room-card';

  const rank = document.createElement('span');
  rank.className = 'rank';
  rank.textContent = `XẾP HẠNG #${item.rank}`;

  const title = document.createElement('h3');
  title.textContent = item.room.title;

  const price = document.createElement('p');
  price.className = 'price';
  price.textContent = formatPrice(item.room.price);

  const meta = document.createElement('p');
  meta.className = 'room-meta';
  meta.textContent = `${item.room.area} m² · ${item.room.district}, ${item.room.city}\n${item.room.room_type}`;

  const scoreRow = document.createElement('div');
  scoreRow.className = 'score-row';
  const finalScore = document.createElement('span');
  finalScore.className = 'score';
  finalScore.textContent = `Điểm cuối: ${(item.final_score * 100).toFixed(1)}%`;
  const faissScore = document.createElement('span');
  faissScore.className = 'score';
  faissScore.textContent = `FAISS: ${(item.faiss_score * 100).toFixed(1)}%`;
  scoreRow.append(finalScore, faissScore);

  const reasons = document.createElement('ul');
  reasons.className = 'reason-list';
  item.reasons.forEach((reason) => {
    const line = document.createElement('li');
    line.textContent = reason;
    reasons.append(line);
  });

  const button = document.createElement('button');
  button.className = 'button button-secondary';
  button.type = 'button';
  button.textContent = 'Ghi nhận lượt xem';
  button.addEventListener('click', () => recordView(item.room.id, button));

  card.append(rank, title, price, meta, scoreRow, reasons, button);
  return card;
}

function renderRecommendations(items) {
  elements.recommendationList.replaceChildren();
  items.forEach((item) => elements.recommendationList.append(createRoomCard(item)));
}

async function loadHealth() {
  try {
    const health = await request('/health');
    elements.health.className = 'health health-ok';
    elements.health.textContent = `API hoạt động · ${health.rooms_indexed.toLocaleString('vi-VN')} phòng`;
  } catch (error) {
    elements.health.className = 'health health-error';
    elements.health.textContent = 'Không kết nối được API';
  }
}

async function loadUsers(preserveSelection = true) {
  const previous = preserveSelection ? elements.userSelect.value : '';
  const users = await request('/demo/users');
  elements.userSelect.replaceChildren();
  users.forEach((user) => {
    const option = document.createElement('option');
    option.value = user.id;
    option.textContent = `${user.display_name} — ${user.view_count} lượt xem`;
    elements.userSelect.append(option);
  });
  if (previous && users.some((user) => user.id === previous)) {
    elements.userSelect.value = previous;
  }
  elements.userSelect.disabled = false;
}

async function loadDemo() {
  const userId = elements.userSelect.value;
  if (!userId) return;
  elements.reloadButton.disabled = true;
  setMessage('Đang tính toán gợi ý…');
  try {
    const [recommendations, history] = await Promise.all([
      request(`/demo/recommendations/${encodeURIComponent(userId)}?k=8`),
      request(`/demo/users/${encodeURIComponent(userId)}/history?limit=8`),
    ]);
    renderSummaries(recommendations);
    renderHistory(history);
    renderRecommendations(recommendations.items);
    setMessage(`Đã tính ${recommendations.items.length} gợi ý trong ${recommendations.processing_time_ms} ms.`);
  } catch (error) {
    setMessage(`Không tải được dữ liệu: ${error.message}`, true);
  } finally {
    elements.reloadButton.disabled = false;
  }
}

async function recordView(roomId, button) {
  button.disabled = true;
  setMessage('Đang ghi nhận lượt xem và tính lại kết quả…');
  try {
    await request('/demo/view-events', {
      method: 'POST',
      body: JSON.stringify({ user_id: elements.userSelect.value, room_id: roomId }),
    });
    await loadUsers(true);
    await loadDemo();
    setMessage('Đã ghi nhận lượt xem. Danh sách gợi ý đã được tính lại.');
  } catch (error) {
    setMessage(`Không ghi nhận được lượt xem: ${error.message}`, true);
    button.disabled = false;
  }
}

async function resetDemo() {
  elements.resetButton.disabled = true;
  setMessage('Đang đặt lại dữ liệu…');
  try {
    await request('/demo/reset', { method: 'POST' });
    await loadUsers(false);
    await loadDemo();
    setMessage('Dữ liệu demo đã trở về trạng thái ban đầu.');
  } catch (error) {
    setMessage(`Không đặt lại được dữ liệu: ${error.message}`, true);
  } finally {
    elements.resetButton.disabled = false;
  }
}

elements.userSelect.addEventListener('change', loadDemo);
elements.reloadButton.addEventListener('click', loadDemo);
elements.resetButton.addEventListener('click', resetDemo);

async function start() {
  await loadHealth();
  try {
    await loadUsers(false);
    await loadDemo();
  } catch (error) {
    setMessage(`Không khởi tạo được demo: ${error.message}`, true);
  }
}

start();
