/* ── 문서 관리 ── */
let editingDocId = null;

async function loadDocs() {
    const res = await fetch('/api/documents');
    const docs = await res.json();
    const container = document.getElementById('doc-list');

    if (docs.length === 0) {
        container.innerHTML = '<div class="empty-state">등록된 문서가 없습니다.</div>';
        return;
    }

    container.innerHTML = `<table>
        <thead><tr><th>제목</th><th>카테고리</th><th>청크</th><th></th></tr></thead>
        <tbody>${docs.map(d => `<tr>
            <td><a href="#" onclick="viewDoc('${d.documentId}');return false">${esc(d.title)}</a></td>
            <td>${esc(d.category || '-')}</td>
            <td>${d.chunkCount}</td>
            <td class="actions">
                <button class="btn btn-sm btn-secondary" onclick="editDoc('${d.documentId}')">수정</button>
                <button class="btn btn-sm btn-danger" onclick="deleteDoc('${d.documentId}')">삭제</button>
            </td>
        </tr>`).join('')}</tbody>
    </table>`;
}

/* 입력 모드 전환 (텍스트/파일) */
function toggleInputMode() {
    const mode = document.querySelector('input[name="inputMode"]:checked').value;
    document.getElementById('text-input-group').style.display = mode === 'text' ? '' : 'none';
    document.getElementById('file-input-group').style.display = mode === 'file' ? 'block' : 'none';
}

/* 모달: 등록/수정 */
function openModal(doc) {
    editingDocId = doc ? doc.documentId : null;
    document.getElementById('modal-title').textContent = doc ? '문서 수정' : '새 문서 등록';
    document.getElementById('modal-submit').textContent = doc ? '수정' : '등록';
    document.getElementById('doc-title').value = doc ? doc.title : '';
    document.getElementById('doc-category').value = doc ? (doc.category || '') : '';
    document.getElementById('doc-content').value = doc ? doc.content : '';
    document.getElementById('doc-file').value = '';
    // 수정 모드에서는 파일 업로드 비활성화
    const modeGroup = document.getElementById('input-mode-group');
    if (doc) {
        modeGroup.style.display = 'none';
        document.getElementById('text-input-group').style.display = '';
        document.getElementById('file-input-group').style.display = 'none';
    } else {
        modeGroup.style.display = '';
        document.querySelector('input[name="inputMode"][value="text"]').checked = true;
        toggleInputMode();
    }
    document.getElementById('doc-modal').classList.add('active');
}

function closeModal() {
    document.getElementById('doc-modal').classList.remove('active');
    editingDocId = null;
}

async function submitDoc() {
    const title = document.getElementById('doc-title').value.trim();
    const category = document.getElementById('doc-category').value.trim();
    const mode = document.querySelector('input[name="inputMode"]:checked').value;

    if (!title) { alert('제목을 입력해 주세요.'); return; }

    if (editingDocId) {
        // 수정 모드: 기존 텍스트 방식
        const content = document.getElementById('doc-content').value.trim();
        if (!content) { alert('내용을 입력해 주세요.'); return; }
        await fetch(`/api/documents/${editingDocId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, content, category: category || null })
        });
    } else if (mode === 'file') {
        // 파일 업로드 모드
        const fileInput = document.getElementById('doc-file');
        if (!fileInput.files.length) { alert('파일을 선택해 주세요.'); return; }
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        formData.append('title', title);
        if (category) formData.append('category', category);
        await fetch('/api/documents/upload', { method: 'POST', body: formData });
    } else {
        // 텍스트 입력 모드
        const content = document.getElementById('doc-content').value.trim();
        if (!content) { alert('내용을 입력해 주세요.'); return; }
        await fetch('/api/documents', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, content, category: category || null })
        });
    }

    closeModal();
    loadDocs();
}

async function editDoc(id) {
    const res = await fetch(`/api/documents/${id}`);
    if (!res.ok) { alert('문서를 불러올 수 없습니다.'); return; }
    const doc = await res.json();
    openModal(doc);
}

async function deleteDoc(id) {
    if (!confirm('정말 삭제하시겠습니까?')) return;
    await fetch(`/api/documents/${id}`, { method: 'DELETE' });
    loadDocs();
}

/* 상세 보기 */
async function viewDoc(id) {
    const res = await fetch(`/api/documents/${id}`);
    if (!res.ok) { alert('문서를 불러올 수 없습니다.'); return; }
    const doc = await res.json();
    document.getElementById('detail-title').textContent = doc.title;
    document.getElementById('detail-meta').textContent = `카테고리: ${doc.category || '-'} | 청크 수: ${doc.chunkCount}`;
    document.getElementById('detail-content').textContent = doc.content;
    document.getElementById('detail-modal').classList.add('active');
}

function closeDetailModal() {
    document.getElementById('detail-modal').classList.remove('active');
}

/* 모달 바깥 클릭으로 닫기 */
document.querySelectorAll('.modal-overlay').forEach(overlay => {
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) overlay.classList.remove('active');
    });
});
