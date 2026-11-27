export interface KPI {
  id?: number;
  name: string;
  description: string;
  value: number;
  unit: string;
  target: number;
  status: 'EXCELLENT' | 'GOOD' | 'WARNING' | 'CRITICAL';
  category: string;
  lastUpdated?: number;
}

