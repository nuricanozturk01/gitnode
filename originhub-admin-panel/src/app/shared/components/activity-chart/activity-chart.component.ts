import { Component, ChangeDetectionStrategy, computed, input } from '@angular/core';

export interface ChartSeries {
  key: string;
  label: string;
  color: string;
  values: number[];
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-activity-chart',
  standalone: true,
  templateUrl: './activity-chart.component.html',
  styleUrl: './activity-chart.component.css',
})
export class ActivityChartComponent {
  readonly labels = input.required<string[]>();
  readonly series = input.required<ChartSeries[]>();
  readonly height = input(220);

  readonly maxValue = computed(() => {
    const values = this.series().flatMap((s) => s.values);
    return Math.max(1, ...values, 0);
  });

  readonly chartWidth = 640;
  readonly padding = { top: 16, right: 16, bottom: 36, left: 36 };

  readonly plotWidth = computed(() => this.chartWidth - this.padding.left - this.padding.right);
  readonly plotHeight = computed(() => this.height() - this.padding.top - this.padding.bottom);

  barGroupWidth(): number {
    const count = this.labels().length || 1;
    return this.plotWidth() / count;
  }

  barX(index: number): number {
    return this.padding.left + index * this.barGroupWidth();
  }

  barHeight(value: number): number {
    return (value / this.maxValue()) * this.plotHeight();
  }

  barY(value: number): number {
    return this.padding.top + this.plotHeight() - this.barHeight(value);
  }

  innerBarWidth(index: number, seriesIndex: number, seriesCount: number): number {
    const group = this.barGroupWidth() * 0.72;
    const gap = 2;
    return (group - gap * (seriesCount - 1)) / seriesCount;
  }

  innerBarX(index: number, seriesIndex: number, seriesCount: number): number {
    const groupStart = this.barX(index) + this.barGroupWidth() * 0.14;
    const barW = this.innerBarWidth(index, seriesIndex, seriesCount);
    return groupStart + seriesIndex * (barW + 2);
  }

  gridLines(): number[] {
    const max = this.maxValue();
    const step = max <= 5 ? 1 : Math.ceil(max / 4);
    const lines: number[] = [];
    for (let v = 0; v <= max; v += step) {
      lines.push(v);
    }
    if (lines[lines.length - 1] !== max) {
      lines.push(max);
    }
    return lines;
  }

  gridY(value: number): number {
    return this.barY(value);
  }
}
