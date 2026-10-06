import { DecimalPipe } from '@angular/common';
import {
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  InjectionToken,
  input,
  viewChild,
} from '@angular/core';
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  ChartConfiguration,
  DoughnutController,
  Filler,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
} from 'chart.js';

Chart.register(
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  DoughnutController,
  Filler,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
);

/** Crée le graphique sur le canvas ; remplacé dans les tests unitaires (jsdom n'a pas de canvas). */
export type ChartFactory = (canvas: HTMLCanvasElement, config: ChartConfiguration) => { destroy(): void };

export const CHART_FACTORY = new InjectionToken<ChartFactory>('CHART_FACTORY', {
  providedIn: 'root',
  factory: () => (canvas, config) => new Chart(canvas, config),
});

export type ChartType = 'bar' | 'doughnut' | 'line';

export interface ChartSeries {
  label: string;
  data: number[];
  /** Une couleur par série, ou une couleur par valeur (anneau, barres d'une seule série). */
  color: string | string[];
}

/**
 * Graphique Chart.js responsive. Les valeurs sont aussi rendues dans un tableau lisible par les lecteurs d'écran
 * (et vérifiable par les tests). Si toutes les valeurs sont nulles, le message `emptyText` remplace le graphique.
 */
@Component({
  selector: 'app-chart',
  imports: [DecimalPipe],
  template: `
    @if (empty()) {
      <div class="empty-state py-4" data-testid="chart-empty">
        <i aria-hidden="true" class="bi bi-bar-chart"></i>{{ emptyText() }}
      </div>
    } @else {
      <div class="chart-canvas" [style.height.px]="height()">
        <canvas #canvas role="img" [attr.aria-label]="label()"></canvas>
      </div>
    }
    <!-- Un tableau ignore width: 1px : le conteneur masqué évite qu'il élargisse la page sur mobile. -->
    <div class="visually-hidden">
      <table data-testid="chart-data">
        <caption>{{ label() }}</caption>
        <thead>
          <tr>
            <th scope="col">Catégorie</th>
            @for (s of series(); track s.label) {
              <th scope="col">{{ s.label }}</th>
            }
          </tr>
        </thead>
        <tbody>
          @for (category of labels(); track $index; let i = $index) {
            <tr>
              <th scope="row">{{ category }}</th>
              @for (s of series(); track s.label) {
                <td>{{ s.data[i] | number: '1.0-1' }}</td>
              }
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .chart-canvas {
      position: relative;
      width: 100%;
    }
  `,
})
export class ChartComponent {
  readonly type = input.required<ChartType>();
  readonly labels = input.required<string[]>();
  readonly series = input.required<ChartSeries[]>();
  /** Titre accessible du graphique. */
  readonly label = input.required<string>();
  readonly emptyText = input('Aucune donnée pour le moment.');
  readonly height = input(260);
  readonly horizontal = input(false);
  readonly stacked = input(false);
  /** Borne de l'axe des valeurs (ex. 100 pour un score) ; sinon automatique. */
  readonly max = input<number | undefined>(undefined);
  /** Ajouté aux valeurs dans les infobulles (ex. « /100 »). */
  readonly unit = input('');

  private readonly factory = inject(CHART_FACTORY);
  private readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('canvas');
  private chart: { destroy(): void } | null = null;

  readonly empty = computed(() => this.series().every((s) => s.data.every((value) => !value)));

  readonly config = computed<ChartConfiguration>(() => {
    const type = this.type();
    const doughnut = type === 'doughnut';
    const unit = this.unit();
    const valueAxis = this.horizontal() ? 'x' : 'y';
    const categoryAxis = this.horizontal() ? 'y' : 'x';
    return {
      type,
      data: {
        labels: this.labels(),
        datasets: this.series().map((s) => ({
          label: s.label,
          data: s.data,
          backgroundColor: s.color,
          borderColor: type === 'line' ? s.color : doughnut ? '#ffffff' : s.color,
          borderWidth: type === 'line' ? 2 : doughnut ? 2 : 0,
          borderRadius: type === 'bar' ? 4 : 0,
          maxBarThickness: 36,
          tension: 0.25,
          pointRadius: 3,
          fill: false,
        })),
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        indexAxis: this.horizontal() ? 'y' : 'x',
        plugins: {
          legend: { display: doughnut || this.series().length > 1, position: 'bottom' },
          tooltip: {
            callbacks: {
              label: (item) => `${item.dataset.label ?? ''} : ${item.formattedValue}${unit}`,
            },
          },
        },
        scales: doughnut
          ? {}
          : {
              [valueAxis]: {
                beginAtZero: true,
                stacked: this.stacked(),
                max: this.max(),
                ticks: { precision: 0 },
              },
              [categoryAxis]: { stacked: this.stacked(), ticks: { autoSkip: false } },
            },
      },
    } as ChartConfiguration;
  });

  constructor() {
    effect(() => {
      const config = this.config();
      const canvas = this.canvas()?.nativeElement;
      this.chart?.destroy();
      this.chart = canvas && !this.empty() ? this.factory(canvas, config) : null;
    });
    inject(DestroyRef).onDestroy(() => this.chart?.destroy());
  }
}
