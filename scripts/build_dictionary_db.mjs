import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { DatabaseSync } from 'node:sqlite';

const backendRoot = path.resolve(import.meta.dirname, '..');
const stagingDir = path.join(backendRoot, 'data', 'dict-staging');
const outputDir = path.join(backendRoot, 'data', 'dictionary');
const mobileCoreOut = path.resolve(backendRoot, '..', 'the-ielts-spells-mobile', 'assets', 'dict', 'core_ielts_dict.json');

fs.mkdirSync(outputDir, { recursive: true });
fs.mkdirSync(path.dirname(mobileCoreOut), { recursive: true });

const dictHhPath = path.join(stagingDir, 'dict_hh.db');
const tdictPath = path.join(stagingDir, 'tdict_en_vi.sqlite');
const outSqlitePath = path.join(outputDir, 'ielts_dict.sqlite');
const outJsonlGzPath = path.join(outputDir, 'dictionary_entries.jsonl.gz');

function stripBomAndTrim(value) {
  if (!value) return '';
  return String(value).replace(/^\uFEFF+/, '').replace(/\s+/g, ' ').trim();
}

function normalizeText(value) {
  return stripBomAndTrim(value)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();
}

function decodeHtmlEntities(str) {
  return str
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function parseDictHhHtml(html, fallbackSummary) {
  if (!html) {
    return fallbackSummary
      ? [{ partOfSpeech: 'general', items: [{ meaning: fallbackSummary, examples: [] }] }]
      : [];
  }

  const meanings = [];
  // Split by <h2>...</h2> POS sections
  const sections = html.split(/<h2>/i);
  for (let i = 0; i < sections.length; i++) {
    const sec = sections[i];
    if (i === 0 && !sec.includes('<li>')) continue;

    let pos = 'general';
    let body = sec;
    const h2Close = sec.indexOf('</h2>');
    if (h2Close !== -1) {
      pos = decodeHtmlEntities(sec.slice(0, h2Close)) || 'general';
      body = sec.slice(h2Close + 5);
    }

    const items = [];
    // Extract top-level <li> blocks inside first <ul>
    const liRegex = /<li>([\s\S]*?)(?=(?:<li>|<\/ul>\s*$|$))/gi;
    // Simpler reliable pass: strip nested <ul ...>...</ul> first to get main meaning, and extract examples inside nested <ul>
    const topUlMatch = body.match(/<ul>([\s\S]*)<\/ul>/i);
    const content = topUlMatch ? topUlMatch[1] : body;

    // Split by top-level <li> where nested <ul> uses <ul style=...>
    const chunks = content.split(/<li>(?![^<]*<\/i><\/li>)/i);
    for (const chunk of chunks) {
      if (!chunk.trim()) continue;
      const nestedMatch = chunk.match(/<ul[^>]*>([\s\S]*?)<\/ul>/i);
      const mainPart = nestedMatch ? chunk.slice(0, nestedMatch.index) : chunk;
      const meaning = decodeHtmlEntities(mainPart);
      if (!meaning) continue;

      const examples = [];
      if (nestedMatch) {
        const exMatches = nestedMatch[1].matchAll(/<li>([\s\S]*?)<\/li>/gi);
        for (const ex of exMatches) {
          const rawEx = ex[1];
          const italicMatch = rawEx.match(/<i>([\s\S]*?)<\/i>/i);
          if (italicMatch) {
            const text = decodeHtmlEntities(rawEx.slice(0, italicMatch.index)).replace(/:\s*$/, '').trim();
            const translation = decodeHtmlEntities(italicMatch[1]);
            if (text || translation) {
              examples.push({ text, translation });
            }
          } else {
            const cleanEx = decodeHtmlEntities(rawEx);
            if (cleanEx) examples.push({ text: cleanEx, translation: '' });
          }
          if (examples.length >= 3) break;
        }
      }

      items.push({ meaning, examples });
      if (items.length >= 12) break;
    }

    if (items.length > 0) {
      meanings.push({ partOfSpeech: pos, items });
    }
  }

  if (meanings.length === 0 && fallbackSummary) {
    meanings.push({
      partOfSpeech: 'general',
      items: [{ meaning: stripBomAndTrim(fallbackSummary), examples: [] }],
    });
  }
  return meanings;
}

console.log('Reading TDict IELTS enrichment DB...');
const tdictDb = new DatabaseSync(tdictPath, { readOnly: true });
const tdictWords = tdictDb.prepare('SELECT * FROM words').all();
const tdictDefs = tdictDb.prepare('SELECT * FROM definitions').all();

const defsByWordId = new Map();
for (const d of tdictDefs) {
  if (!defsByWordId.has(d.word_id)) defsByWordId.set(d.word_id, []);
  defsByWordId.get(d.word_id).push(d);
}

const tdictMap = new Map();
for (const w of tdictWords) {
  const key = stripBomAndTrim(w.word).toLowerCase();
  if (!key) continue;
  const defs = defsByWordId.get(w.id) || [];
  let wordFamily = [];
  try { wordFamily = JSON.parse(w.word_family || '[]'); } catch {}
  tdictMap.set(key, {
    word: stripBomAndTrim(w.word),
    phoneticUk: stripBomAndTrim(w.phonetic_uk),
    phoneticUs: stripBomAndTrim(w.phonetic_us),
    audioUrl: stripBomAndTrim(w.audio_url),
    cefrLevel: stripBomAndTrim(w.cefr_level),
    etymology: stripBomAndTrim(w.etymology),
    wordFamily,
    frequencyRank: w.frequency_rank ?? 5000,
    definitions: defs.map((d) => {
      let synonyms = [];
      let antonyms = [];
      try { synonyms = JSON.parse(d.synonyms || '[]'); } catch {}
      try { antonyms = JSON.parse(d.antonyms || '[]'); } catch {}
      return {
        partOfSpeech: stripBomAndTrim(d.part_of_speech),
        definitionEn: stripBomAndTrim(d.definition_en),
        definitionVi: stripBomAndTrim(d.definition_vi),
        exampleEn: stripBomAndTrim(d.example_en),
        exampleVi: stripBomAndTrim(d.example_vi),
        synonyms,
        antonyms,
      };
    }),
  });
}
tdictDb.close();
console.log(`Loaded ${tdictMap.size} TDict IELTS words.`);

console.log('Opening dict_hh.db and building unified ielts_dict.sqlite...');
const hhDb = new DatabaseSync(dictHhPath, { readOnly: true });

if (fs.existsSync(outSqlitePath)) fs.unlinkSync(outSqlitePath);
const outDb = new DatabaseSync(outSqlitePath);
outDb.exec(`
  PRAGMA journal_mode = OFF;
  PRAGMA synchronous = OFF;
  CREATE TABLE dictionary_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    direction TEXT NOT NULL,
    headword TEXT NOT NULL,
    headword_lower TEXT NOT NULL,
    normalized_headword TEXT NOT NULL,
    ipa TEXT DEFAULT '',
    cefr_level TEXT DEFAULT '',
    part_of_speech TEXT DEFAULT '',
    summary TEXT DEFAULT '',
    payload_json TEXT NOT NULL,
    source TEXT NOT NULL
  );
`);

const insertStmt = outDb.prepare(`
  INSERT INTO dictionary_entries (
    direction, headword, headword_lower, normalized_headword,
    ipa, cefr_level, part_of_speech, summary, payload_json, source
  ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
`);

const gzipStream = zlib.createGzip({ level: 6 });
const jsonlOut = fs.createWriteStream(outJsonlGzPath);
gzipStream.pipe(jsonlOut);

let totalCount = 0;
const seenKeys = new Set();
const coreMobileEntries = [];

function addEntry({ direction, headword, ipa, cefrLevel, partOfSpeech, summary, payload, source, isCoreCandidate }) {
  const cleanHead = stripBomAndTrim(headword);
  if (!cleanHead) return;
  const lower = cleanHead.toLowerCase();
  const norm = normalizeText(cleanHead);
  const dedupKey = `${direction}::${lower}`;
  if (seenKeys.has(dedupKey)) return;
  seenKeys.add(dedupKey);

  const payloadJson = JSON.stringify(payload);
  insertStmt.run(
    direction,
    cleanHead,
    lower,
    norm,
    ipa || '',
    cefrLevel || '',
    partOfSpeech || '',
    summary || '',
    payloadJson,
    source
  );

  gzipStream.write(
    JSON.stringify({
      direction,
      headword: cleanHead,
      headwordLower: lower,
      normalizedHeadword: norm,
      ipa: ipa || null,
      cefrLevel: cefrLevel || null,
      partOfSpeech: partOfSpeech || null,
      summary: summary || null,
      payload,
      source,
    }) + '\n'
  );

  if (isCoreCandidate && coreMobileEntries.length < 2200) {
    coreMobileEntries.push({
      direction,
      headword: cleanHead,
      headwordLower: lower,
      normalizedHeadword: norm,
      ipa: ipa || null,
      cefrLevel: cefrLevel || null,
      partOfSpeech: partOfSpeech || null,
      summary: summary || null,
      payload,
      source,
    });
  }

  totalCount++;
}

outDb.exec('BEGIN TRANSACTION');

// 1. Process EN -> VI from dict_hh (av) enriched with TDict
for (const row of hhDb.prepare('SELECT word, html, description, pronounce FROM av').iterate()) {
  const cleanHead = stripBomAndTrim(row.word);
  if (!cleanHead) continue;
  const lower = cleanHead.toLowerCase();
  const tdict = tdictMap.get(lower);

  const hhMeanings = parseDictHhHtml(row.html, stripBomAndTrim(row.description));
  let ipa = stripBomAndTrim(row.pronounce);
  if (ipa && !ipa.startsWith('/')) ipa = `/${ipa}/`;

  let cefrLevel = '';
  let primaryPos = hhMeanings[0]?.partOfSpeech || '';
  let summary = stripBomAndTrim(row.description);
  const payload = {
    meanings: hhMeanings,
  };

  if (tdict) {
    ipa = tdict.phoneticUk || tdict.phoneticUs || ipa;
    cefrLevel = tdict.cefrLevel || '';
    if (tdict.definitions.length > 0) {
      primaryPos = tdict.definitions[0].partOfSpeech || primaryPos;
      if (!summary) summary = tdict.definitions[0].definitionVi;
      payload.ieltsDefinitions = tdict.definitions;
    }
    if (tdict.phoneticUk) payload.phoneticUk = tdict.phoneticUk;
    if (tdict.phoneticUs) payload.phoneticUs = tdict.phoneticUs;
    if (tdict.audioUrl) payload.audioUrl = tdict.audioUrl;
    if (tdict.wordFamily?.length) payload.wordFamily = tdict.wordFamily;
    if (tdict.etymology) payload.etymology = tdict.etymology;
    tdictMap.delete(lower);
  }

  addEntry({
    direction: 'EN_VI',
    headword: cleanHead,
    ipa,
    cefrLevel,
    partOfSpeech: primaryPos,
    summary,
    payload,
    source: tdict ? 'AVDICT+TDICT' : 'AVDICT',
    isCoreCandidate: Boolean(tdict),
  });
}

// 2. Remaining TDict words not in dict_hh
for (const [, tdict] of tdictMap.entries()) {
  const primaryDef = tdict.definitions[0];
  addEntry({
    direction: 'EN_VI',
    headword: tdict.word,
    ipa: tdict.phoneticUk || tdict.phoneticUs,
    cefrLevel: tdict.cefrLevel,
    partOfSpeech: primaryDef?.partOfSpeech || '',
    summary: primaryDef?.definitionVi || '',
    payload: {
      phoneticUk: tdict.phoneticUk,
      phoneticUs: tdict.phoneticUs,
      audioUrl: tdict.audioUrl,
      wordFamily: tdict.wordFamily,
      etymology: tdict.etymology,
      ieltsDefinitions: tdict.definitions,
      meanings: tdict.definitions.map((d) => ({
        partOfSpeech: d.partOfSpeech,
        items: [
          {
            meaning: d.definitionVi,
            examples: d.exampleEn ? [{ text: d.exampleEn, translation: d.exampleVi }] : [],
          },
        ],
      })),
    },
    source: 'TDICT',
    isCoreCandidate: true,
  });
}

// 3. Process VI -> EN from dict_hh (va)
let viCoreAdded = 0;
for (const row of hhDb.prepare('SELECT word, html, description, pronounce FROM va').iterate()) {
  const cleanHead = stripBomAndTrim(row.word);
  if (!cleanHead) continue;
  const meanings = parseDictHhHtml(row.html, stripBomAndTrim(row.description));
  const primaryPos = meanings[0]?.partOfSpeech || '';
  const summary = stripBomAndTrim(row.description);

  const isCore = viCoreAdded < 400 && cleanHead.length <= 24;
  if (isCore) viCoreAdded++;

  addEntry({
    direction: 'VI_EN',
    headword: cleanHead,
    ipa: stripBomAndTrim(row.pronounce),
    cefrLevel: '',
    partOfSpeech: primaryPos,
    summary,
    payload: { meanings },
    source: 'VADICT',
    isCoreCandidate: isCore,
  });
}

outDb.exec('COMMIT');
console.log('Creating SQLite indexes...');
outDb.exec(`
  CREATE INDEX idx_dict_dir_lower ON dictionary_entries(direction, headword_lower);
  CREATE INDEX idx_dict_dir_norm ON dictionary_entries(direction, normalized_headword);
  CREATE INDEX idx_dict_lower ON dictionary_entries(headword_lower);
`);

hhDb.close();
outDb.close();

await new Promise((resolve) => {
  gzipStream.end();
  jsonlOut.on('finish', resolve);
});

fs.writeFileSync(mobileCoreOut, JSON.stringify(coreMobileEntries));

const sqliteSizeMb = (fs.statSync(outSqlitePath).size / 1048576).toFixed(2);
const gzSizeMb = (fs.statSync(outJsonlGzPath).size / 1048576).toFixed(2);
const coreSizeKb = (fs.statSync(mobileCoreOut).size / 1024).toFixed(1);

console.log(`Done! Total entries: ${totalCount}`);
console.log(`- SQLite offline DB: ${outSqlitePath} (${sqliteSizeMb} MB)`);
console.log(`- Backend JSONL.GZ:  ${outJsonlGzPath} (${gzSizeMb} MB)`);
console.log(`- Mobile Core JSON:  ${mobileCoreOut} (${coreSizeKb} KB, ${coreMobileEntries.length} entries)`);
