import { Pipe, PipeTransform } from '@angular/core';
import {
  APPLICATION_STATUS_LABELS,
  CONTRACT_TYPE_LABELS,
  EDUCATION_LEVEL_LABELS,
  JOB_STATUS_LABELS,
  ROLE_LABELS,
} from '../core/models';

const LABELS = {
  contract: CONTRACT_TYPE_LABELS,
  education: EDUCATION_LEVEL_LABELS,
  jobStatus: JOB_STATUS_LABELS,
  applicationStatus: APPLICATION_STATUS_LABELS,
  role: ROLE_LABELS,
} as const;

export type LabelKind = keyof typeof LABELS;

/** Libellé français d'une valeur d'énumération du backend : {{ offer.contractType | label: 'contract' }}. */
@Pipe({ name: 'label' })
export class LabelPipe implements PipeTransform {
  transform(value: string | null | undefined, kind: LabelKind): string {
    if (!value) {
      return '—';
    }
    return (LABELS[kind] as Record<string, string>)[value] ?? value;
  }
}
