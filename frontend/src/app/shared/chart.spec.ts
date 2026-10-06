import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { Component, LOCALE_ID, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ChartConfiguration } from 'chart.js';
import { ChartComponent, ChartSeries, ChartType } from './chart';
import { fakeChartFactory } from './fake-chart.testing';

registerLocaleData(localeFr);

@Component({
  imports: [ChartComponent],
  template: `<app-chart [type]="type()" label="Graphique de test" [labels]="labels()" [series]="series()"
    [horizontal]="true" [stacked]="true" [max]="100" emptyText="Rien à afficher" />`,
})
class Host {
  readonly type = signal<ChartType>('bar');
  readonly labels = signal(['Dev Java', 'Dev Python']);
  readonly series = signal<ChartSeries[]>([
    { label: 'En attente', data: [2, 0], color: '#f59e0b' },
    { label: 'Refusé', data: [1, 3.25], color: '#dc2626' },
  ]);
}

describe('ChartComponent', () => {
  function setup() {
    const charts = fakeChartFactory();
    TestBed.configureTestingModule({ imports: [Host], providers: [charts.provider, { provide: LOCALE_ID, useValue: 'fr-FR' }] });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    return { charts, fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('creates a responsive Chart.js chart from the inputs', () => {
    const { charts, el } = setup();
    expect(charts.configs).toHaveLength(1);
    expect(charts.canvases[0]).toBe(el.querySelector('canvas'));
    const config = charts.configs[0] as ChartConfiguration<'bar'>;
    expect(config.type).toBe('bar');
    expect(config.data.labels).toEqual(['Dev Java', 'Dev Python']);
    expect(config.data.datasets.map((d) => d.data)).toEqual([[2, 0], [1, 3.25]]);
    expect(config.data.datasets.map((d) => d.backgroundColor)).toEqual(['#f59e0b', '#dc2626']);
    expect(config.options?.responsive).toBe(true);
    expect(config.options?.maintainAspectRatio).toBe(false);
    expect(config.options?.indexAxis).toBe('y');
    expect(config.options?.scales?.['x']).toMatchObject({ stacked: true, max: 100, beginAtZero: true });
    expect(config.options?.scales?.['y']).toMatchObject({ stacked: true });
    expect(el.querySelector('canvas')?.getAttribute('aria-label')).toBe('Graphique de test');
  });

  it('renders the same values in an accessible table', () => {
    const { el } = setup();
    const table = el.querySelector('[data-testid=chart-data]')!;
    expect(table.querySelector('caption')?.textContent).toBe('Graphique de test');
    const rows = [...table.querySelectorAll('tbody tr')].map((row) =>
      [...row.querySelectorAll('th, td')].map((cell) => cell.textContent?.trim()),
    );
    expect(rows).toEqual([
      ['Dev Java', '2', '1'],
      ['Dev Python', '0', '3,3'],
    ]);
  });

  it('shows the empty message instead of a chart when every value is zero', () => {
    const { charts, fixture, el } = setup();
    fixture.componentInstance.series.set([{ label: 'En attente', data: [0, 0], color: '#f59e0b' }]);
    fixture.detectChanges();
    expect(el.querySelector('canvas')).toBeNull();
    expect(el.querySelector('[data-testid=chart-empty]')?.textContent).toContain('Rien à afficher');
    expect(charts.destroyed).toBe(1);
    expect(charts.configs).toHaveLength(1);
  });

  it('rebuilds the chart when the data changes and destroys it with the component', () => {
    const { charts, fixture } = setup();
    fixture.componentInstance.type.set('line');
    fixture.detectChanges();
    expect(charts.configs).toHaveLength(2);
    expect(charts.configs[1].type).toBe('line');
    expect(charts.destroyed).toBe(1);
    fixture.destroy();
    expect(charts.destroyed).toBe(2);
  });
});
