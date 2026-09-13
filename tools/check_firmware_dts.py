#!/usr/bin/env python3
"""Mandatory conservative preflight of the generated Zephyr DTS, before battery use.
Not a replacement for measuring charge current or checking the physical board.
"""
import argparse,re
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('dts',type=Path);a=p.parse_args();text=a.dts.read_text()
def val(block,key):
 m=re.search(r'\b'+re.escape(key)+r'\s*=\s*<\s*(0x[0-9a-fA-F]+|[0-9]+)',block)
 if not m:raise ValueError('Missing '+key)
 return int(m[1],0)
try:
 blocks=re.findall(r'[^{}]*\{([^{}]*compatible\s*=\s*"nordic,npm1300-charger"[^{}]*)\}',text,re.S)
 if len(blocks)!=1:raise ValueError('Expected exactly one charger node; inspect BSP/overlay instead of guessing')
 b=blocks[0]
 for k,v in {'current-microamp':100000,'term-microvolt':4200000,'thermistor-ohms':10000}.items():
  if val(b,k)!=v:raise ValueError(f'{k} is not {v}')
 if 'charging-enable;' not in b:raise ValueError('Charging is not enabled')
 for label in ('hvb_audio','hvb_settings','voice-button','power_en','dmic_vdd','pdm20','py25q64'):
  if label not in text:raise ValueError('Missing board label '+label)
 print('DTS labels and charger values passed. Manually verify audio partition 0..0x77ffff, settings 0x780000..0x7fffff, mic rail 3.3 V, and no competing D0 use.')
except Exception as e:p.exit(1,f'PREFLIGHT FAILED: {e}\nDo not connect the LiPo until corrected and checked.\n')
