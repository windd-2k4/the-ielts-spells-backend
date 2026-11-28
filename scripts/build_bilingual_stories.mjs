import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const backendRoot = path.resolve(import.meta.dirname, '..');
const sqlitePath = path.join(backendRoot, 'data', 'dictionary', 'ielts_dict.sqlite');
const cachePath = path.join(backendRoot, 'data', 'stories', 'translation_cache.json');
const outPath = path.resolve(
  backendRoot,
  '..',
  'the-ielts-spells-mobile',
  'assets',
  'stories',
  'bilingual_stories_bank.json'
);

fs.mkdirSync(path.dirname(cachePath), { recursive: true });
fs.mkdirSync(path.dirname(outPath), { recursive: true });

let translationCache = {};
if (fs.existsSync(cachePath)) {
  try {
    translationCache = JSON.parse(fs.readFileSync(cachePath, 'utf8'));
  } catch {
    translationCache = {};
  }
}

function saveCache() {
  fs.writeFileSync(cachePath, JSON.stringify(translationCache), 'utf8');
}

const CEFR_ORDER = { A1: 1, A2: 2, B1: 3, B2: 4, C1: 5, C2: 6 };

function cleanMeaningsShort(raw) {
  if (!raw) return '';
  return String(raw)
    .replace(/\([^)]*\)/g, '')
    .split(/[;.\n]/)[0]
    .replace(/\s+/g, ' ')
    .trim();
}

function loadCefrLexicon() {
  const lexicon = new Map();
  if (!fs.existsSync(sqlitePath)) {
    console.warn('Warning: ielts_dict.sqlite not found at', sqlitePath);
    return lexicon;
  }
  const db = new DatabaseSync(sqlitePath, { readOnly: true });
  const rows = db
    .prepare(
      `SELECT headword_lower, headword, cefr_level, ipa, part_of_speech, summary
       FROM dictionary_entries
       WHERE direction = 'EN_VI' AND cefr_level IS NOT NULL AND trim(cefr_level) != ''`
    )
    .all();
  db.close();

  for (const r of rows) {
    const level = String(r.cefr_level || '').toUpperCase().match(/\b(A1|A2|B1|B2|C1|C2)\b/)?.[1];
    if (!level) continue;
    const key = String(r.headword_lower || '').trim();
    if (!key || key.includes(' ')) continue;
    lexicon.set(key, {
      word: r.headword,
      cefr: level,
      rank: CEFR_ORDER[level],
      ipa: r.ipa || '',
      pos: r.part_of_speech || '',
      meaningVi: cleanMeaningsShort(r.summary || ''),
    });
  }
  return lexicon;
}

async function translateSingle(text, sl = 'en', tl = 'vi') {
  const clean = String(text || '').trim();
  if (!clean) return '';
  const cacheKey = `${sl}->${tl}::${clean}`;
  if (translationCache[cacheKey]) return translationCache[cacheKey];

  const url =
    `https://translate.googleapis.com/translate_a/single?client=gtx` +
    `&sl=${encodeURIComponent(sl)}&tl=${encodeURIComponent(tl)}&dt=t&q=${encodeURIComponent(clean)}`;

  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const out = Array.isArray(data?.[0])
        ? data[0].map((seg) => (Array.isArray(seg) ? seg[0] : '')).join('').trim()
        : '';
      if (out) {
        translationCache[cacheKey] = out;
        return out;
      }
    } catch {
      await new Promise((r) => setTimeout(r, 350 * (attempt + 1)));
    }
  }
  return clean;
}

async function translateBatch(items, sl = 'en', tl = 'vi') {
  const missing = [];
  for (const item of items) {
    const clean = String(item || '').trim();
    if (!clean) continue;
    const cacheKey = `${sl}->${tl}::${clean}`;
    if (!translationCache[cacheKey]) {
      missing.push(clean);
    }
  }
  if (missing.length === 0) {
    return items.map((item) => {
      const clean = String(item || '').trim();
      return clean ? translationCache[`${sl}->${tl}::${clean}`] || clean : '';
    });
  }

  // Pack up to 14 items separated by unique delimiter lines to preserve 1:1 alignment
  const CHUNK_SIZE = 12;
  const SEP = ' ||| ';
  for (let i = 0; i < missing.length; i += CHUNK_SIZE) {
    const chunk = missing.slice(i, i + CHUNK_SIZE);
    const joined = chunk.map((s, idx) => `[${idx}] ${s}`).join('\n');
    const url =
      `https://translate.googleapis.com/translate_a/single?client=gtx` +
      `&sl=${encodeURIComponent(sl)}&tl=${encodeURIComponent(tl)}&dt=t&q=${encodeURIComponent(joined)}`;

    let aligned = false;
    try {
      const res = await fetch(url);
      if (res.ok) {
        const data = await res.json();
        const fullText = Array.isArray(data?.[0])
          ? data[0].map((seg) => (Array.isArray(seg) ? seg[0] : '')).join('')
          : '';
        const lines = fullText
          .split('\n')
          .map((l) => l.trim())
          .filter(Boolean);
        if (lines.length === chunk.length) {
          let allMatched = true;
          const parsed = [];
          for (let j = 0; j < chunk.length; j++) {
            const m = lines[j].match(/^\[(\d+)\]\s*(.+)$/);
            if (!m || Number(m[1]) !== j) {
              allMatched = false;
              break;
            }
            parsed.push(m[2].trim());
          }
          if (allMatched) {
            for (let j = 0; j < chunk.length; j++) {
              translationCache[`${sl}->${tl}::${chunk[j]}`] = parsed[j];
            }
            aligned = true;
          }
        }
      }
    } catch {
      // Fallback below
    }

    if (!aligned) {
      await Promise.all(chunk.map((item) => translateSingle(item, sl, tl)));
    }
    await new Promise((r) => setTimeout(r, 90));
  }

  return items.map((item) => {
    const clean = String(item || '').trim();
    return clean ? translationCache[`${sl}->${tl}::${clean}`] || clean : '';
  });
}

function splitEnglishSentences(text) {
  const normalized = String(text || '')
    .replace(/\s+/g, ' ')
    .trim();
  if (!normalized) return [];
  const rawParts = normalized.split(/(?<=[.!?]["”']?)\s+(?=[A-Z“"'])/);
  const out = [];
  for (const part of rawParts) {
    const p = part.trim();
    if (!p) continue;
    if (out.length > 0 && p.length < 28 && out[out.length - 1].length < 130) {
      out[out.length - 1] = `${out[out.length - 1]} ${p}`;
    } else {
      out.push(p);
    }
  }
  return out;
}

function toTitleCase(str) {
  const minor = new Set(['and', 'the', 'of', 'in', 'on', 'at', 'to', 'for', 'a', 'an', 'his', 'her', 'with']);
  return String(str || '')
    .toLowerCase()
    .split(/\s+/)
    .map((w, idx) => (idx > 0 && minor.has(w) ? w : w.charAt(0).toUpperCase() + w.slice(1)))
    .join(' ');
}

function extractHighlights(sentences, cefrLevel, lexicon) {
  const minRank = Math.max(1, (CEFR_ORDER[cefrLevel] || 2) - 1);
  const found = new Map();
  for (const sentence of sentences) {
    const tokens = String(sentence).toLowerCase().match(/\b[a-z]{4,}\b/g) || [];
    for (const tok of tokens) {
      if (found.has(tok)) continue;
      const entry = lexicon.get(tok);
      if (!entry || !entry.meaningVi) continue;
      if (entry.rank >= minRank) {
        found.set(tok, {
          word: entry.word,
          cefr: entry.cefr,
          ipa: entry.ipa,
          pos: entry.pos,
          meaningVi: entry.meaningVi,
        });
      }
    }
  }
  return [...found.values()]
    .sort((a, b) => (CEFR_ORDER[b.cefr] || 0) - (CEFR_ORDER[a.cefr] || 0))
    .slice(0, 8);
}

function computeAesopScore(sentences, lexicon) {
  const words = sentences.join(' ').toLowerCase().match(/\b[a-z]{3,}\b/g) || [];
  let rankSum = 0;
  let rankedCount = 0;
  let highWords = 0;
  let c1c2Words = 0;
  for (const w of words) {
    const e = lexicon.get(w);
    if (e) {
      rankSum += e.rank;
      rankedCount++;
      if (e.rank >= 4) highWords++;
      if (e.rank >= 5) c1c2Words++;
    }
  }
  const avgWordRank = rankedCount > 0 ? rankSum / rankedCount : 2.0;
  const avgSentLen = sentences.length > 0 ? words.length / sentences.length : 12;
  return avgWordRank * 1.45 + avgSentLen * 0.08 + highWords * 0.14 + c1c2Words * 0.28;
}

function mapThemeCategory(themes = [], source = 'aesop') {
  if (source === 'aesop') return 'Ngụ ngôn & Triết lý';
  const joined = themes.join(' ').toLowerCase();
  if (joined.includes('food') || joined.includes('culinary')) return 'Ẩm thực & Văn hóa';
  if (joined.includes('travel') || joined.includes('city') || joined.includes('nature')) return 'Du lịch & Khám phá';
  if (joined.includes('history') || joined.includes('culture')) return 'Văn hóa & Xã hội';
  if (joined.includes('work') || joined.includes('study') || joined.includes('tech')) return 'Học tập & Công việc';
  return 'Đời sống hàng ngày';
}

function generateAesopQuiz(fable) {
  if (!fable.moral || fable.moral.length < 15) return [];
  const distractors = [
    'Always compete against others regardless of the cost.',
    'Success depends purely on luck rather than careful judgment.',
    'Never trust advice from experienced friends or neighbors.',
  ];
  return [
    {
      prompt: `What is the main lesson (moral) of "${toTitleCase(fable.title)}"?`,
      choices: [fable.moral, distractors[0], distractors[1]],
      correctIndex: 0,
      explanation: `Moral of the story: ${fable.moral}`,
    },
  ];
}

console.log('Loading CEFR lexicon from SQLite...');
const lexicon = loadCefrLexicon();
console.log(`Loaded ${lexicon.size} single-word CEFR entries.`);

console.log('Fetching open-source story datasets...');
const [meloRaw, aesopRaw] = await Promise.all([
  fetch('https://raw.githubusercontent.com/MeloLingua/melolingua-language-stories-dataset/main/data/stories.json').then((r) => r.json()),
  fetch('https://raw.githubusercontent.com/itayniv/aesop-fables-stories/master/public/aesopFables.json').then((r) => r.json()),
]);

const candidates = [];

// 1. Process MeloLingua CEFR-Graded Stories (A1, A2, B1, B2)
const meloBuckets = { A1: [], A2: [], B1: [], B2: [] };
for (const s of meloRaw) {
  const lvl = String(s.cefr_level || '').toUpperCase();
  if (!meloBuckets[lvl]) continue;
  const engSentences = [];
  for (const sent of s.sentences || []) {
    const enParts = splitEnglishSentences(sent.translation || '');
    engSentences.push(...enParts);
  }
  if (engSentences.length < 4 || engSentences.length > 42) continue;
  meloBuckets[lvl].push({
    id: `melo_${s.id}`,
    rawTitle: s.title,
    needEnglishTitleTranslation: true,
    cefr: lvl,
    category: mapThemeCategory(s.themes || [], 'melolingua'),
    summaryEn: String(s.summary || '').trim(),
    sentencesEn: engSentences,
    moralEn: '',
    readingMinutes: Math.max(1, Number(s.reading_minutes) || Math.ceil(engSentences.length / 6)),
    questions: (s.comprehension_questions || []).slice(0, 3).map((q) => ({
      prompt: q.prompt,
      choices: q.choices || [],
      correctIndex: Number(q.correct_index) || 0,
      explanation: q.explanation || '',
    })),
    source: 'MeloLingua (CC BY-NC 4.0)',
  });
}

// Balance MeloLingua up to 14 per level (A1..B2)
for (const lvl of ['A1', 'A2', 'B1', 'B2']) {
  candidates.push(...meloBuckets[lvl].slice(0, 14));
}

// 2. Process Aesop's Fables (Public Domain) -> fill A1..C2 including famous classics first
const preferredTitles = new Set([
  'THE DOG AND HIS SHADOW',
  'THE FOX AND THE GRAPES',
  'THE LION AND THE MOUSE',
  'THE TORTOISE AND THE HARE',
  'THE BOY WHO CRIED WOLF',
  'THE ANT AND THE GRASSHOPPER',
  'THE CROW AND THE PITCHER',
  'THE WOLF AND THE KID',
  'THE TOWN MOUSE AND THE COUNTRY MOUSE',
  'THE GOOSE THAT LAID THE GOLDEN EGGS',
  'BELLING THE CAT',
  'THE FOX AND THE CROW',
]);

const aesopSorted = [...(aesopRaw.stories || [])].sort((a, b) => {
  const ap = preferredTitles.has(a.title) ? 0 : 1;
  const bp = preferredTitles.has(b.title) ? 0 : 1;
  return ap - bp;
});

const scoredAesop = [];
for (const f of aesopSorted) {
  const sentencesEn = [];
  for (const s of f.story || []) {
    sentencesEn.push(...splitEnglishSentences(s));
  }
  if (sentencesEn.length < 4 || sentencesEn.length > 36) continue;
  scoredAesop.push({
    f,
    sentencesEn,
    score: computeAesopScore(sentencesEn, lexicon),
    preferred: preferredTitles.has(f.title),
  });
}

// Sort by lexical/structural difficulty so A1..C2 levels correspond meaningfully to complexity
const aesopByDifficulty = [...scoredAesop].sort((a, b) => a.score - b.score);
const totalAesop = aesopByDifficulty.length;
const selectedAesop = [];
const levelQuotas = [
  { level: 'A1', startRatio: 0.0, endRatio: 0.15, max: 3 },
  { level: 'A2', startRatio: 0.15, endRatio: 0.35, max: 4 },
  { level: 'B1', startRatio: 0.35, endRatio: 0.55, max: 4 },
  { level: 'B2', startRatio: 0.55, endRatio: 0.72, max: 6 },
  { level: 'C1', startRatio: 0.72, endRatio: 0.88, max: 10 },
  { level: 'C2', startRatio: 0.88, endRatio: 1.0, max: 8 },
];

for (const q of levelQuotas) {
  const start = Math.floor(totalAesop * q.startRatio);
  const end = Math.ceil(totalAesop * q.endRatio);
  const slice = aesopByDifficulty
    .slice(start, end)
    .sort((a, b) => (a.preferred === b.preferred ? a.score - b.score : a.preferred ? -1 : 1))
    .slice(0, q.max);
  for (const item of slice) {
    selectedAesop.push({ ...item, cefr: q.level });
  }
}

for (const { f, sentencesEn, cefr } of selectedAesop) {
  const cleanTitle = toTitleCase(f.title);
  candidates.push({
    id: `aesop_${f.number}_${cleanTitle.toLowerCase().replace(/[^a-z0-9]+/g, '_')}`,
    rawTitle: cleanTitle,
    needEnglishTitleTranslation: false,
    cefr,
    category: 'Ngụ ngôn & Triết lý',
    summaryEn: String(f.moral || sentencesEn[0] || '').trim(),
    sentencesEn,
    moralEn: String(f.moral || '').trim(),
    readingMinutes: Math.max(1, Math.ceil(sentencesEn.length / 6)),
    questions: generateAesopQuiz(f),
    source: "Aesop's Fables (Public Domain)",
  });
}

console.log(`Prepared ${candidates.length} stories across CEFR levels. Translating titles, summaries & sentences...`);

// Translate non-English MeloLingua titles -> English first
const titlesToEnIdx = [];
const titlesToEnText = [];
candidates.forEach((c, idx) => {
  if (c.needEnglishTitleTranslation) {
    titlesToEnIdx.push(idx);
    titlesToEnText.push(c.rawTitle);
  } else {
    c.titleEn = c.rawTitle;
  }
});

if (titlesToEnText.length > 0) {
  const translatedTitles = await translateBatch(titlesToEnText, 'auto', 'en');
  titlesToEnIdx.forEach((storyIdx, i) => {
    candidates[storyIdx].titleEn = toTitleCase(translatedTitles[i].replace(/["“”]/g, '').trim() || candidates[storyIdx].rawTitle);
  });
}

// Now translate story text (EN -> VI) story by story, saving cache periodically
const builtStories = [];
for (let idx = 0; idx < candidates.length; idx++) {
  const c = candidates[idx];
  const textsToTranslate = [
    c.titleEn,
    c.summaryEn,
    ...(c.moralEn ? [c.moralEn] : []),
    ...c.sentencesEn,
  ];
  const translated = await translateBatch(textsToTranslate, 'en', 'vi');
  let ptr = 0;
  const titleVi = translated[ptr++];
  const summaryVi = translated[ptr++];
  const moralVi = c.moralEn ? translated[ptr++] : '';
  const sentencesVi = translated.slice(ptr);

  const sentencePairs = c.sentencesEn.map((en, sIdx) => ({
    id: `s${sIdx + 1}`,
    en,
    vi: sentencesVi[sIdx] || '',
  }));

  // Translate comprehension questions if present
  const quizItems = [];
  for (const q of c.questions || []) {
    if (!q.prompt || !q.choices?.length) continue;
    const qTexts = [q.prompt, q.explanation || '', ...q.choices];
    const qVi = await translateBatch(qTexts, 'en', 'vi');
    quizItems.push({
      promptEn: q.prompt,
      promptVi: qVi[0] || '',
      choices: q.choices,
      choicesVi: qVi.slice(2),
      correctIndex: q.correctIndex,
      explanationVi: qVi[1] || '',
    });
  }

  const highlights = extractHighlights(c.sentencesEn, c.cefr, lexicon);
  const wordCount = c.sentencesEn.join(' ').split(/\s+/).filter(Boolean).length;

  builtStories.push({
    id: c.id,
    titleEn: c.titleEn,
    titleVi,
    cefr: c.cefr,
    category: c.category,
    readingMinutes: c.readingMinutes,
    wordCount,
    summaryEn: c.summaryEn,
    summaryVi,
    moralEn: c.moralEn || null,
    moralVi: moralVi || null,
    highlights,
    sentences: sentencePairs,
    questions: quizItems,
    source: c.source,
  });

  if ((idx + 1) % 10 === 0 || idx === candidates.length - 1) {
    saveCache();
    console.log(`Processed ${idx + 1}/${candidates.length} stories...`);
  }
}

// Sort deterministically: CEFR A1 -> C2, classics first inside each level
builtStories.sort((a, b) => {
  const rankDiff = (CEFR_ORDER[a.cefr] || 9) - (CEFR_ORDER[b.cefr] || 9);
  if (rankDiff !== 0) return rankDiff;
  return a.titleEn.localeCompare(b.titleEn);
});

const summaryCounts = {};
for (const s of builtStories) {
  summaryCounts[s.cefr] = (summaryCounts[s.cefr] || 0) + 1;
}

const payload = {
  version: '1.0.0',
  generatedAt: new Date().toISOString(),
  attribution: [
    "Aesop's Fables (Public Domain via itayniv/aesop-fables-stories)",
    'MeloLingua CEFR-Graded Multilingual Stories Dataset, CC BY-NC 4.0 (https://github.com/MeloLingua/melolingua-language-stories-dataset)',
  ],
  countsByCefr: summaryCounts,
  stories: builtStories,
};

fs.writeFileSync(outPath, JSON.stringify(payload), 'utf8');
const kb = (fs.statSync(outPath).size / 1024).toFixed(1);
console.log(`Generated ${outPath} (${kb} KB). Counts by CEFR:`, summaryCounts);
