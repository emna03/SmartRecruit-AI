import { ChartConfiguration } from 'chart.js';
import { CHART_FACTORY, ChartFactory } from './chart';

/** Remplace Chart.js dans les tests unitaires (jsdom n'a pas de canvas) : garde les graphiques demandés. */
export function fakeChartFactory() {
  const configs: ChartConfiguration[] = [];
  const canvases: HTMLCanvasElement[] = [];
  let destroyed = 0;
  const factory: ChartFactory = (canvas, config) => {
    canvases.push(canvas);
    configs.push(config);
    return { destroy: () => destroyed++ };
  };
  return {
    configs,
    canvases,
    get destroyed() {
      return destroyed;
    },
    provider: { provide: CHART_FACTORY, useValue: factory },
  };
}
