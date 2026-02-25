/* ── 유틸 ── */
function esc(str) {
    const d = document.createElement('div');
    d.textContent = str;
    return d.innerHTML;
}

/* ── 탭 전환 ── */
document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
        tab.classList.add('active');
        document.getElementById(tab.dataset.tab).classList.add('active');
        if (tab.dataset.tab === 'docs') loadDocs();
    });
});

/* ── 채팅 ── */
const chatArea = document.getElementById('chat-area');
const input = document.getElementById('question');
const sendBtn = document.getElementById('send-btn');
let conversationId = sessionStorage.getItem('conversationId') || crypto.randomUUID();
sessionStorage.setItem('conversationId', conversationId);

/* 세션 목록 로드 */
async function loadSessions() {
    try {
        const res = await fetch('/api/chat/sessions');
        if (!res.ok) return;
        const sessions = await res.json();
        const list = document.getElementById('session-list');
        if (sessions.length === 0) {
            list.innerHTML = '<div style="text-align:center;color:rgba(255,255,255,0.4);padding:20px;font-size:13px;">대화가 없습니다</div>';
            return;
        }
        list.innerHTML = sessions.map(s => `
            <div class="session-item ${s.conversationId === conversationId ? 'active' : ''}"
                 onclick="loadSession('${s.conversationId}')">
                <span class="title">${esc(s.title)}</span>
                <button class="delete-btn" onclick="event.stopPropagation();deleteSession('${s.conversationId}')" title="삭제">&times;</button>
            </div>
        `).join('');
    } catch (e) { /* 무시 */ }
}

/* 세션 전환 */
async function loadSession(id) {
    conversationId = id;
    sessionStorage.setItem('conversationId', id);
    chatArea.innerHTML = '';
    try {
        const res = await fetch(`/api/chat/history?conversationId=${id}`);
        if (!res.ok) return;
        const messages = await res.json();
        messages.forEach(m => addMsg(m.content, m.role === 'user' ? 'user' : m.role === 'system' ? 'system' : 'bot'));
    } catch (e) { /* 무시 */ }
    loadSessions();
    input.focus();
}

/* 세션 삭제 */
async function deleteSession(id) {
    if (!confirm('이 대화를 삭제하시겠습니까?')) return;
    try {
        await fetch(`/api/chat/sessions/${id}`, { method: 'DELETE' });
    } catch (e) { /* 무시 */ }
    if (id === conversationId) {
        newChat();
    }
    loadSessions();
}

/* 새 대화 */
function newChat() {
    chatArea.innerHTML = '';
    conversationId = crypto.randomUUID();
    sessionStorage.setItem('conversationId', conversationId);
    loadSessions();
    input.focus();
}

/* 페이지 로드 시 대화 이력 복원 + 세션 목록 로드 */
(async () => {
    loadSessions();
    try {
        const res = await fetch(`/api/chat/history?conversationId=${conversationId}`);
        if (!res.ok) return;
        const messages = await res.json();
        messages.forEach(m => addMsg(m.content, m.role === 'user' ? 'user' : m.role === 'system' ? 'system' : 'bot'));
    } catch (e) { /* 이력 없으면 무시 */ }
})();

function addMsg(text, cls) {
    const div = document.createElement('div');
    div.className = 'msg ' + cls;
    div.textContent = text;
    chatArea.appendChild(div);
    chatArea.scrollTop = chatArea.scrollHeight;
    return div;
}

async function send() {
    const q = input.value.trim();
    if (!q) return;
    input.value = '';
    input.style.height = 'auto';
    addMsg(q, 'user');
    const botMsg = addMsg('', 'bot');
    sendBtn.disabled = true;

    try {
        const res = await fetch('/api/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question: q, conversationId })
        });
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const events = buffer.split('\n\n');
            buffer = events.pop();
            for (const event of events) {
                const lines = event.replace(/\r/g, '').split('\n');
                let eventType = '';
                for (const line of lines) {
                    if (line.startsWith('event:')) {
                        eventType = line.substring(6).trim();
                    } else if (line.startsWith('data:')) {
                        const data = line.substring(5);
                        if (eventType === 'conversationId') {
                            conversationId = data;
                            sessionStorage.setItem('conversationId', data);
                        } else {
                            botMsg.textContent += data;
                            chatArea.scrollTop = chatArea.scrollHeight;
                        }
                    }
                }
            }
        }
    } catch (e) {
        botMsg.textContent = '오류가 발생했습니다. 다시 시도해 주세요.';
    } finally {
        sendBtn.disabled = false;
        loadSessions();
        input.focus();
    }
}

sendBtn.addEventListener('click', send);
input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
});
input.addEventListener('input', () => {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 120) + 'px';
});
