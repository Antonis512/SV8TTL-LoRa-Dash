// Equal tuples can have several regional names. Source: https://api.meshcore.nz/api/v1/config (2026-11-02)
const meshCorePresets = [
  ["Australia", 915.800, 250, 10, 5],
  ["Australia (Narrow)", 916.575, 62.5, 7, 7],
  ["Australia (Mid)", 915.075, 125, 9, 5],
  ["Australia: SA, WA", 923.125, 62.5, 8, 8],
  ["Australia: QLD", 923.125, 62.5, 8, 5],
  ["Brazil", 923.125, 62.5, 8, 8],
  ["Canada", 910.525, 62.5, 7, 5],
  ["Costa Rica", 910.525, 125, 11, 5],
  ["EU/UK (Narrow)", 869.618, 62.5, 8, 8],
  ["EU/UK (Deprecated)", 869.525, 250, 11, 5],
  ["Czech Republic (Narrow)", 869.432, 62.5, 7, 5],
  ["EU 433MHz (Long Range)", 433.650, 250, 11, 5],
  ["EU 433MHz (Narrow)", 433.650, 62.5, 8, 8],
  ["Hungary", 869.618, 62.5, 7, 5],
  ["Netherlands", 869.618, 62.5, 7, 5],
  ["Netherlands (Limburg)", 869.618, 62.5, 8, 8],
  ["New Zealand (Narrow)", 917.375, 62.5, 7, 5],
  ["New Zealand (Gisborne)", 917.375, 250, 11, 5],
  ["Portugal 433", 433.375, 62.5, 9, 6],
  ["Portugal 868", 869.618, 62.5, 7, 6],
  ["Slovakia", 869.618, 62.5, 7, 5],
  ["Switzerland", 869.618, 62.5, 8, 8],
  ["Thailand", 921, 62.5, 7, 5],
  ["USA", 910.525, 62.5, 7, 5],
  ["USA - Southern California", 927.875, 62.5, 7, 5],
  ["Vietnam (Narrow)", 920.250, 62.5, 8, 5],
  ["Vietnam (Deprecated)", 920.250, 250, 11, 5],
];
// Sub-GHz Meshtastic modem settings (BW in kHz, SF, CR denominator).
// https://meshtastic.org/docs/overview/radio-settings/
const meshtasticPresets = [
  ['Short Turbo',500,7,5], ['Short Fast',250,7,5], ['Short Slow',250,8,5],
  ['Medium Turbo',500,9,5], ['Medium Fast',250,9,5], ['Medium Slow',250,10,5],
  ['Long Turbo',500,11,8], ['Long Fast',250,11,5], ['Long Moderate',125,11,8],
  ['Long Slow',125,12,8], ['Very Long Slow',62.5,12,8],
  ['Lite Fast',125,9,5], ['Lite Slow',125,10,5], ['Narrow Fast',62.5,7,6],
  ['Narrow Slow',62.5,8,6], ['Tiny Fast',15.6,7,5], ['Tiny Slow',15.6,8,6]
];

function heardProfile(network, raw) {
  if (!raw) return 'Settings: Unknown';
  const settings = raw.replace(/^Custom · /, '');
  const mc = network === 'mc';
  const fields = mc
    ? /^(\d+(?:\.\d+)?) MHz · BW (\d+(?:\.\d+)?) kHz · SF(\d+) · CR4\/(\d+)$/.exec(settings)
    : /^BW (\d+(?:\.\d+)?) kHz · SF(\d+) · CR4\/(\d+)$/.exec(settings);
  if (!fields) {
    if (!mc && meshtasticPresets.some(([name]) => name === raw)) return 'Preset: ' + raw;
    return 'Settings: ' + settings;
  }
  const values = fields.slice(1).map(Number);
  const presets = mc ? meshCorePresets : meshtasticPresets;
  const matches = presets.filter(([, ...tuple]) => tuple.every((v, i) => v === values[i]));
  return matches.length ? 'Preset: ' + matches.map(([name]) => name).join(' / ') : 'Settings: ' + settings;
}

function heardDescription(network, raw) {
  return 'Heard (last known): Network: ' + network.toUpperCase() + ', ' + heardProfile(network, raw);
}
