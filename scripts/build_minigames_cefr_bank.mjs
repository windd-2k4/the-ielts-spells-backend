import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const backendRoot = path.resolve(import.meta.dirname, '..');
const sqlitePath = path.join(backendRoot, 'data', 'dictionary', 'ielts_dict.sqlite');
const outPath = path.resolve(
  backendRoot,
  '..',
  'the-ielts-spells-mobile',
  'assets',
  'games',
  'minigames_cefr_bank.json'
);
fs.mkdirSync(path.dirname(outPath), { recursive: true });

function parseCsvLine(line) {
  const result = [];
  let cur = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (ch === ',' && !inQuotes) {
      result.push(cur.trim());
      cur = '';
    } else {
      cur += ch;
    }
  }
  result.push(cur.trim());
  return result;
}

function normalizeCefr(raw) {
  if (!raw) return null;
  const upper = String(raw).toUpperCase().trim();
  const match = upper.match(/\b(A1|A2|B1|B2|C1|C2)\b/);
  return match ? match[1] : null;
}

function shortenViMeaning(raw) {
  if (!raw) return '';
  let s = String(raw)
    .replace(/\([^)]*\)/g, '')
    .split(/[;.\n]/)[0]
    .replace(/\s+/g, ' ')
    .trim();
  if (s.length > 52) {
    const parts = s.split(',').map((p) => p.trim()).filter(Boolean);
    if (parts[0] && parts[0].length <= 52) {
      s = parts.length > 1 && `${parts[0]}, ${parts[1]}`.length <= 52
          ? `${parts[0]}, ${parts[1]}`
          : parts[0];
    } else {
      return '';
    }
  }
  return s;
}

function shuffleDeterministic(arr, seed = 42) {
  const out = [...arr];
  let s = seed;
  for (let i = out.length - 1; i > 0; i--) {
    s = (s * 1664525 + 1013904223) % 4294967296;
    const j = s % (i + 1);
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

console.log('Fetching open-source CEFR & Collocation datasets from GitHub...');
const [ox3kCsv, ox5kRaw, cefrjCsv, octanoveCsv] = await Promise.all([
  fetch('https://raw.githubusercontent.com/ciwga/Oxford3000_Vocab/main/oxford3000_vocabulary_with_collocations_and_definitions_datasets.csv').then((r) => r.text()),
  fetch('https://raw.githubusercontent.com/tyypgzl/Oxford-5000-words/main/full-word.json').then((r) => r.json()),
  fetch('https://raw.githubusercontent.com/openlanguageprofiles/olp-en-cefrj/master/cefrj-vocabulary-profile-1.5.csv').then((r) => r.text()),
  fetch('https://raw.githubusercontent.com/openlanguageprofiles/olp-en-cefrj/master/octanove-vocabulary-profile-c1c2-1.0.csv').then((r) => r.text()),
]);

const wordToCefr = new Map();

// 1. Oxford 5000 JSON CEFR tags
for (const entry of Array.isArray(ox5kRaw) ? ox5kRaw : []) {
  const v = entry?.value;
  if (!v?.word) continue;
  const lvl = normalizeCefr(v.level);
  if (lvl && !wordToCefr.has(v.word.toLowerCase())) {
    wordToCefr.set(v.word.toLowerCase(), lvl);
  }
}

// 2. CEFR-J A1-B2 CSV
for (const line of cefrjCsv.split(/\r?\n/).slice(1)) {
  if (!line.trim()) continue;
  const cols = parseCsvLine(line);
  const headword = cols[0]?.toLowerCase();
  const lvl = normalizeCefr(cols[2]);
  if (headword && lvl && !wordToCefr.has(headword)) {
    wordToCefr.set(headword, lvl);
  }
}

// 3. Octanove C1-C2 CSV
for (const line of octanoveCsv.split(/\r?\n/).slice(1)) {
  if (!line.trim()) continue;
  const cols = parseCsvLine(line);
  const headword = cols[0]?.toLowerCase();
  const lvl = normalizeCefr(cols[2]);
  if (headword && lvl && !wordToCefr.has(headword)) {
    wordToCefr.set(headword, lvl);
  }
}

// 4. Enrich from local SQLite TDict (7,101 words with Vietnamese definitions & CEFR)
const db = new DatabaseSync(sqlitePath, { readOnly: true });
const speedVocabPool = { A1: [], A2: [], B1: [], B2: [], C1: [], C2: [] };

for (const row of db.prepare(`
  SELECT headword, headword_lower, ipa, cefr_level, part_of_speech, payload_json
  FROM dictionary_entries
  WHERE source IN ('AVDICT+TDICT', 'TDICT')
    AND cefr_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')
`).iterate()) {
  const lvl = row.cefr_level;
  if (!wordToCefr.has(row.headword_lower)) {
    wordToCefr.set(row.headword_lower, lvl);
  }

  let payload = {};
  try { payload = JSON.parse(row.payload_json || '{}'); } catch {}
  const def = payload?.ieltsDefinitions?.[0];
  const rawMeaning = def?.definitionVi || '';
  const meaning = shortenViMeaning(rawMeaning);
  const pos = def?.partOfSpeech || row.part_of_speech || 'word';
  const ipa = payload?.phoneticUk || payload?.phoneticUs || row.ipa || '';

  if (
    row.headword &&
    !row.headword.includes(' ') &&
    meaning.length >= 2 &&
    meaning.length <= 58 &&
    !/^[a-zA-Z\s-]+$/.test(meaning)
  ) {
    speedVocabPool[lvl].push({
      id: `sv_${lvl.toLowerCase()}_${speedVocabPool[lvl].length + 1}`,
      word: row.headword,
      pos,
      phonetic: ipa,
      correctMeaning: meaning,
    });
  }
}
db.close();

// Build final Speed Vocab items (120 per level with 3 unique distractors from same level)
const speedVocabByCefr = {};
for (const lvl of ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']) {
  const pool = shuffleDeterministic(speedVocabPool[lvl], lvl.charCodeAt(0) * 100 + lvl.charCodeAt(1));
  const selected = pool.slice(0, 120);
  speedVocabByCefr[lvl] = selected.map((item, idx) => {
    const distractors = [];
    for (let offset = 1; distractors.length < 3 && offset < pool.length; offset++) {
      const candidate = pool[(idx + offset * 7) % pool.length];
      if (
        candidate.word !== item.word &&
        candidate.correctMeaning !== item.correctMeaning &&
        !distractors.includes(candidate.correctMeaning)
      ) {
        distractors.push(candidate.correctMeaning);
      }
    }
    const options = shuffleDeterministic([item.correctMeaning, ...distractors], idx + 17);
    return {
      ...item,
      options,
    };
  });
}

// 5. Build Collocation Hunter pairs across A1-C2 from Oxford3000_Vocab + curated IELTS academic collocations
const collocationsByCefr = { A1: [], A2: [], B1: [], B2: [], C1: [], C2: [] };
const seenCollocations = new Set();

function pushCollocation(lvl, left, right) {
  const l = left.trim().replace(/^["']|["']$/g, '');
  const r = right.trim().replace(/^["']|["']$/g, '');
  if (!l || !r || l.length < 2 || r.length < 2) return;
  if (l.includes('(') || r.includes('(') || r.length > 28 || l.length > 18) return;
  const key = `${l.toLowerCase()}||${r.toLowerCase()}`;
  if (seenCollocations.has(key)) return;
  seenCollocations.add(key);
  collocationsByCefr[lvl].push({ left: l, right: r });
}

for (const line of ox3kCsv.split(/\r?\n/).slice(1)) {
  if (!line.trim()) continue;
  const cols = parseCsvLine(line);
  const headword = cols[0]?.toLowerCase();
  const collRaw = cols[8];
  if (!headword || !collRaw || collRaw === 'none') continue;

  const lvl = wordToCefr.get(headword) || 'B1';
  for (const phrase of collRaw.split(',')) {
    const clean = phrase.replace(/\s+/g, ' ').trim();
    const parts = clean.split(' ');
    if (parts.length >= 2 && parts.length <= 4) {
      pushCollocation(lvl, parts[0], parts.slice(1).join(' '));
    }
  }
}

// Guarantee rich A1/A2 everyday collocations & C1/C2 IELTS academic collocations
const curatedExtras = {
  A1: [
    ['drink', 'cold water'], ['eat', 'breakfast'], ['read', 'a book'], ['go', 'to school'],
    ['play', 'football'], ['watch', 'TV'], ['listen', 'to music'], ['open', 'the door'],
    ['wash', 'your hands'], ['brush', 'your teeth'], ['ride', 'a bike'], ['write', 'a letter'],
    ['speak', 'English'], ['wear', 'a jacket'], ['buy', 'fresh fruit'], ['clean', 'the room'],
    ['cook', 'dinner'], ['draw', 'a picture'], ['ask', 'a question'], ['answer', 'the phone'],
    ['take', 'a bus'], ['have', 'lunch'], ['make', 'tea'], ['turn on', 'the light'],
  ],
  A2: [
    ['make', 'a mistake'], ['take', 'a photo'], ['heavy', 'rain'], ['strong', 'wind'],
    ['catch', 'a cold'], ['do', 'homework'], ['save', 'money'], ['spend', 'time'],
    ['miss', 'the train'], ['keep', 'a secret'], ['tell', 'the truth'], ['pay', 'the bill'],
    ['book', 'a ticket'], ['pack', 'a suitcase'], ['win', 'a prize'], ['lose', 'weight'],
    ['join', 'a club'], ['visit', 'a museum'], ['share', 'ideas'], ['check', 'email'],
  ],
  C1: [
    ['exacerbate', 'existing disparities'], ['mitigate', 'climate risks'], ['alleviate', 'urban poverty'],
    ['curb', 'carbon emissions'], ['foster', 'social cohesion'], ['allocate', 'scarce resources'],
    ['bridge', 'the digital divide'], ['yield', 'promising results'], ['pose', 'a grave threat'],
    ['trigger', 'economic downturn'], ['harness', 'renewable energy'], ['implement', 'strict policies'],
    ['promote', 'sustainable growth'], ['address', 'pressing concerns'], ['attain', 'academic excellence'],
    ['exert', 'profound influence'], ['cultivate', 'critical thinking'], ['maintain', 'ecological balance'],
  ],
  C2: [
    ['wreak', 'havoc on ecosystems'], ['shed', 'light on the enigma'], ['cast', 'doubt on findings'],
    ['grapple', 'with dire consequences'], ['run', 'contrary to belief'], ['exercise', 'utmost restraint'],
    ['strike', 'a delicate balance'], ['reap', 'tangible benefits'], ['precipitate', 'an economic crisis'],
    ['engender', 'mutual trust'], ['stem', 'from systemic flaws'], ['hinge', 'on global cooperation'],
    ['catalyze', 'scientific breakthroughs'], ['herald', 'a transformative era'], ['attenuate', 'social friction'],
    ['disseminate', 'scholarly research'], ['jeopardize', 'long-term viability'], ['underscore', 'paramount urgency'],
    ['substantiate', 'empirical claims'], ['corroborate', 'the hypothesis'], ['refute', 'prevailing dogma'],
    ['elucidate', 'complex mechanisms'], ['circumvent', 'legal loopholes'], ['ameliorate', 'harsh conditions'],
    ['obviate', 'the need for'], ['perpetuate', 'social inequality'], ['stifle', 'creative expression'],
    ['reconcile', 'divergent viewpoints'], ['culminate', 'in historic reform'], ['epitomize', 'modern architecture'],
    ['exacerbate', 'existing tensions'], ['relinquish', 'monopoly power'], ['bolster', 'economic resilience'],
    ['espouse', 'radical ideals'], ['exemplify', 'ethical leadership'], ['curtail', 'wasteful expenditure'],
    ['galvanize', 'public support'], ['inhibit', 'cellular growth'], ['mitigate', 'adverse effects'],
    ['proliferate', 'across borders'],
  ],
};

for (const [lvl, pairs] of Object.entries(curatedExtras)) {
  for (const [left, right] of pairs) {
    pushCollocation(lvl, left, right);
  }
}

for (const lvl of ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']) {
  collocationsByCefr[lvl] = shuffleDeterministic(collocationsByCefr[lvl], lvl.charCodeAt(1) * 31).slice(0, 120);
}

const output = {
  version: '2026.11.cefr-1',
  sources: [
    'ciwga/Oxford3000_Vocab (Collocations)',
    'tyypgzl/Oxford-5000-words (CEFR A1-C1)',
    'openlanguageprofiles/olp-en-cefrj (CEFR-J & Octanove C1-C2)',
    'pyhyper/TDict (IELTS EN-VI Definitions & IPA)',
  ],
  collocations: collocationsByCefr,
  speedVocab: speedVocabByCefr,
};

fs.writeFileSync(outPath, JSON.stringify(output));
const kb = (fs.statSync(outPath).size / 1024).toFixed(1);
console.log(`Generated ${outPath} (${kb} KB)`);
for (const lvl of ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']) {
  console.log(`  ${lvl}: collocations=${collocationsByCefr[lvl].length}, speedVocab=${speedVocabByCefr[lvl].length}`);
}
