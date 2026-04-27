import { writeFileSync } from 'node:fs'

const parts = [
  [1, 'BATCH-2024-001', 3], [2, 'BATCH-2024-002', 3], [3, 'BATCH-2024-003', 3],
  [4, 'BATCH-2024-004', 8], [5, 'BATCH-2024-005', 4], [6, 'BATCH-2024-006', 3],
  [7, 'BATCH-2024-007', 3], [8, 'BATCH-2024-008', 6], [9, 'BATCH-2024-009', 2],
  [10, 'BATCH-2024-010', 4], [11, 'BATCH-2024-011', 5], [12, 'BATCH-2024-012', 15],
  [13, 'BATCH-2024-013', 2], [14, 'BATCH-2024-014', 3], [15, 'BATCH-2024-015', 5],
  [16, 'BATCH-2024-016', 6], [17, 'BATCH-2024-017', 12], [18, 'BATCH-2024-018', 18],
  [19, 'BATCH-2024-019', 3], [20, 'BATCH-2024-020', 1],
]

const rows = []
for (const [id, bn, qty] of parts) {
  for (let i = 1; i <= qty; i++) {
    const code = `${bn}-U${String(i).padStart(3, '0')}`
    const st = id === 1 && i === 3 ? 1 : 0
    rows.push(`(${id}, '${bn}', '${code}', ${st})`)
  }
}

const sql = `-- 按 parts_info.quantity 展开的全量单件种子（共 ${rows.length} 条）\nINSERT IGNORE INTO parts_unit (parts_id, batch_number, unit_code, unit_status) VALUES\n${rows.join(',\n')};\n`
writeFileSync(new URL('../src/main/resources/db/parts_unit_seed.sql', import.meta.url), sql)
console.log('written', rows.length, 'units')
