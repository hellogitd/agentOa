import * as echarts from 'echarts/core';
import { BarChart, GaugeChart, LineChart, PieChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

/**
 * echarts 按需引入（docs/22 FE-F4-03）：仅注册业务用到的图表与组件，
 * 避免 `import * as echarts` 全量打包；页面统一从本模块取 `echarts`。
 */
echarts.use([BarChart, GaugeChart, LineChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer]);

export { echarts };
export type { EChartsCoreOption, EChartsType } from 'echarts/core';
