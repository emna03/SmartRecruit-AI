import { FormControl, FormGroup } from '@angular/forms';
import { notInFuture, orderedFields, PHONE_PATTERN, todayIso, WEBSITE_PATTERN } from './validators';

describe('validators', () => {
  it('orderedFields compares ISO dates', () => {
    const form = new FormGroup(
      { start: new FormControl('2025-06-01'), end: new FormControl('2025-01-01') },
      { validators: orderedFields('start', 'end', 'dateOrder') },
    );
    expect(form.hasError('dateOrder')).toBe(true);

    form.controls.end.setValue('2025-12-31');
    expect(form.hasError('dateOrder')).toBe(false);

    form.controls.end.setValue('');
    expect(form.valid).toBe(true);
  });

  it('orderedFields compares numbers (salaries)', () => {
    const form = new FormGroup(
      { min: new FormControl<number | null>(3000), max: new FormControl<number | null>(1000) },
      { validators: orderedFields('min', 'max', 'salaryOrder') },
    );
    expect(form.hasError('salaryOrder')).toBe(true);

    form.controls.max.setValue(3000);
    expect(form.hasError('salaryOrder')).toBe(false);

    form.controls.max.setValue(null);
    expect(form.hasError('salaryOrder')).toBe(false);
  });

  it('notInFuture rejects future dates only', () => {
    expect(notInFuture(new FormControl('2099-01-01'))).toEqual({ future: true });
    expect(notInFuture(new FormControl(todayIso()))).toBeNull();
    expect(notInFuture(new FormControl(''))).toBeNull();
  });

  it('patterns match the backend rules', () => {
    expect(PHONE_PATTERN.test('+216 20 123 456')).toBe(true);
    expect(PHONE_PATTERN.test('abc')).toBe(false);
    expect(WEBSITE_PATTERN.test('https://acme.example')).toBe(true);
    expect(WEBSITE_PATTERN.test('ftp://acme')).toBe(false);
  });
});
