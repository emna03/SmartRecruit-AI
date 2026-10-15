import { MatchingResult } from '../app/core/models';

/** Résultat de matching de test (format renvoyé par Spring Boot). */
export function matchingResult(overrides: Partial<MatchingResult> = {}): MatchingResult {
  return {
    score: 82,
    skillsScore: 75,
    experienceScore: 100,
    educationScore: 80,
    matchedSkills: [
      { name: 'Java', matchedBy: 'Java', matchType: 'EXACT', credit: 1 },
      { name: 'SQL', matchedBy: 'MySQL', matchType: 'IMPLIED', credit: 1 },
      { name: 'Angular', matchedBy: 'React', matchType: 'RELATED', credit: 0.5 },
    ],
    missingSkills: ['Docker', 'Kubernetes'],
    explanation: 'Score global : 82 % — compatibilité élevée.\nCompétences : 75 % (3 sur 4 requises).',
    algorithmVersion: '1.0.0',
    computedAt: '2026-10-06T10:00:00',
    stale: false,
    semanticSimilarity: 0.84,
    semanticScore: 84,
    semanticModel: 'sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2',
    ...overrides,
  };
}
