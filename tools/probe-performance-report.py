#!/usr/bin/env python3
"""从真实后端探针结果生成离线曲线、原始 CSV 和同负载比较，无第三方依赖。"""
import argparse
import csv
import json
import math
from pathlib import Path


ENVIRONMENT_KEYS = ('minecraft', 'java', 'vm', 'os', 'arch', 'processors', 'max_heap_bytes', 'tick_rate', 'world_seed', 'jvm_tuning', 'config')


def read_runs(directory):
    runs = []
    for phase in ('prepare', 'resume'):
        path = directory / f'{phase}-report.json'
        if path.is_file():
            report = json.loads(path.read_text(encoding='utf-8-sig'))
            for result in report['results']:
                if 'performance' in result:
                    runs.append(dict(phase=phase, step=result['step'], verdict=result['verdict'],
                                     environment=report['environment'], workload=result.get('workload'),
                                     window=result.get('window'), performance=result['performance']))
            if report.get('interrupted_performance'):
                runs.append(dict(phase=phase, step='interrupted', verdict=report['verdict'],
                                 environment=report['environment'], workload=None, window=None,
                                 performance=report['interrupted_performance']))
    return runs


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)] if ordered else None


def summary(run):
    perf = run['performance']
    # CPU 取完整落入 MEASURE 的区间，以区间时长加权；短生命周期世界探针使用全部区间。
    measured = [phase for phase in perf['phases'] if phase['name'] == 'MEASURE']
    ticks = [tick for tick in perf['ticks'] if not measured or tick['phase'] == 'MEASURE']
    samples = [sample for sample in perf['resources'] if not measured or any(
        sample['elapsed_ms'] <= phase['end_ms'] and sample['elapsed_ms'] - sample['interval_ms'] >= phase['start_ms']
        for phase in measured)]

    def weighted(key):
        valid = [sample for sample in samples if sample.get(key) is not None]
        duration = sum(sample['interval_ms'] for sample in valid)
        return sum(sample[key] * sample['interval_ms'] for sample in valid) / duration if duration else None

    return dict(scope='MEASURE' if measured else 'whole_step', ticks=len(ticks), samples=len(samples),
                vanilla_p95_ms=percentile([tick['vanilla_ms'] for tick in ticks], .95),
                runtime_p95_ms=percentile([tick['runtime_ms'] for tick in ticks if tick.get('runtime_ms') is not None], .95),
                server_tick_p95_ms=percentile([tick['vanilla_ms'] + tick['runtime_ms'] for tick in ticks if tick.get('runtime_ms') is not None], .95),
                process_cpu_percent_one_core=weighted('process_cpu_percent_one_core'),
                server_thread_cpu_percent=weighted('server_thread_cpu_percent'),
                peak_heap_used_mib=max((sample['heap_used_bytes'] / 2**20 for sample in samples), default=None),
                sampling_total_ms=perf['sampling_total_ms'], tick_recording_total_ms=perf['tick_recording_total_ms'])


def export_csv(directory, runs):
    for name, key in (('resources', 'resources'), ('ticks', 'ticks'), ('phases', 'phases')):
        rows = []
        for run in runs:
            for item in run['performance'][key]:
                row = dict(process_phase=run['phase'], step=run['step'], **item)
                kinds = row.pop('kind_step_us', None)
                if kinds is not None:
                    row.update({f'{kind.lower()}_us': value for kind, value in zip(run['performance']['kind_order'], kinds)})
                rows.append(row)
        if rows:
            with (directory / f'performance-{name}.csv').open('w', encoding='utf-8-sig', newline='') as stream:
                writer = csv.DictWriter(stream, list(dict.fromkeys(key for row in rows for key in row)))
                writer.writeheader()
                writer.writerows(rows)


def comparison(current, previous):
    rows = []
    for run in current:
        old = next((item for item in previous if (item['phase'], item['step']) == (run['phase'], run['step'])), None)
        if old is None:
            rows.append(dict(step=run['step'], comparable=False, reason='缺少对应步骤'))
            continue
        differences = [key for key in ENVIRONMENT_KEYS if run['environment'].get(key) != old['environment'].get(key)]
        if run['workload'] != old['workload']:
            differences.append('workload')
        if run['verdict'] != 'PASS' or old['verdict'] != 'PASS':
            differences.append('verdict')
        if differences:
            rows.append(dict(step=run['step'], comparable=False, reason='配置或结果不同：' + ', '.join(differences)))
            continue
        before, after = summary(old), summary(run)
        deltas = {key: (after[key] / before[key] - 1) * 100 if before.get(key) and after.get(key) is not None else None
                  for key in ('runtime_p95_ms', 'server_tick_p95_ms', 'process_cpu_percent_one_core', 'peak_heap_used_mib')}
        rows.append(dict(step=run['step'], comparable=True, before=before, after=after, delta_percent=deltas))
    return rows


def validate(runs):
    errors = []
    if not runs:
        return ['没有性能数据']
    for run in runs:
        perf = run['performance']
        if perf.get('sampling_error'):
            errors.append(f"{run['step']}: {perf['sampling_error']}")
        if len(perf['resources']) < 2 or not perf['phases']:
            errors.append(f"{run['step']}: 资源或阶段样本缺失")
        if perf['duration_ms'] > 1000 and not perf['ticks']:
            errors.append(f"{run['step']}: tick 样本缺失")
        if perf['duration_ms'] > 1000:
            for key in ('process_cpu_percent_one_core', 'server_thread_cpu_percent'):
                if not any(sample.get(key) is not None for sample in perf['resources']):
                    errors.append(f"{run['step']}: {key} 未取得有效样本")
        if run['step'].startswith('bench:') and run['verdict'] == 'PASS':
            names = {phase['name'] for phase in perf['phases']}
            if not {'SETUP', 'WARMUP', 'MEASURE', 'ASSERT', 'CLEANUP'} <= names:
                errors.append(f"{run['step']}: 生命周期阶段缺失")
        if any(tick.get('runtime_ms') is None for tick in perf['ticks']):
            errors.append(f"{run['step']}: 正式运行时计时缺失")
    return errors


PAGE = '''<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><title>IDTW 后端性能探针</title>
<style>
body{font:15px system-ui,sans-serif;margin:32px auto;max-width:1200px;padding:0 20px;color:#dfe9f3;background:#101720}
h1,h2,h3{color:#fff}section{background:#1a2532;margin:22px 0;padding:18px;border-radius:10px}
table{border-collapse:collapse;width:100%;margin:12px 0}td,th{text-align:left;padding:7px;border-bottom:1px solid #384759}
svg{width:100%;height:auto;background:#111b27}text{fill:#aab9c9;font:12px sans-serif}pre{white-space:pre-wrap;word-break:break-word}
.legend span{margin-right:18px}.chart{margin:20px 0}.readout{min-height:25px;color:#b7d9f6}a{color:#9cd3ff}
</style>
<h1>IDTW 后端性能探针</h1>
<p>CPU：JVM 进程 / 服务端线程累计 CPU 时间差值 ÷ 实际采样间隔，100% = 占用一个逻辑核；进程可超过 100%。
内存：JVM heap used / committed 与 non-heap used，单位 MiB。GC 为本步骤累计值。采样间隔 200 ms，移动鼠标查看原始值。</p>
<p>阶段表为独占墙钟耗时，包含等待 tick；CPU 列为阶段内服务端线程 CPU 时间。tick 图使用原版计时加 IDTW 运行时；调度分类属于 runtime 内部，彼此不与 runtime 相加。</p>
<p>曲线覆盖步骤全程；统计与对比优先使用 MEASURE，世界链路使用整步。报告写盘和序列化在采样窗口外。
资源采样与逐 tick 记录成本在每步列出。首次 JVM 的 JIT、GC 和机器负载会影响数值，比较时沿用相同负载并重复运行。</p>
<div id="overview"></div><div id="comparison"></div><main id="runs"></main>
<script id="data" type="application/json">__DATA__</script>
<script>
const data=JSON.parse(document.getElementById('data').textContent), colors=['#61c9ff','#ffb55c','#75db9a','#d49aff','#ff7f93'];
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt=v=>v==null?'未提供':typeof v==='number'?v.toFixed(3):esc(v);
const table=(heads,rows)=>'<table><tr>'+heads.map(h=>'<th>'+esc(h)+'</th>').join('')+'</tr>'+rows.map(r=>'<tr>'+r.map(v=>'<td>'+v+'</td>').join('')+'</tr>').join('')+'</table>';
function chart(parent,title,rows,series,phases,duration,unit){
 const box=document.createElement('div');box.className='chart';parent.appendChild(box);
 box.innerHTML='<h3>'+esc(title)+'</h3><div class="legend">'+series.map((s,i)=>'<span style="color:'+colors[i%colors.length]+'">'+esc(s[0])+'</span>').join('')+'</div>';
 const w=1120,h=240,left=65,right=15,top=18,bottom=30,xmax=Math.max(duration,1), ymax=Math.max(1,...rows.flatMap(r=>series.map(s=>s[1](r)||0)))*1.06;
 const x=v=>left+v/xmax*(w-left-right), y=v=>h-bottom-v/ymax*(h-top-bottom);
 let svg='<svg viewBox="0 0 '+w+' '+h+'" role="img" aria-label="'+esc(title)+'">';
 phases.forEach((p,i)=>{svg+='<rect x="'+x(p.start_ms)+'" y="'+top+'" width="'+Math.max(0,x(p.end_ms)-x(p.start_ms))+'" height="'+(h-top-bottom)+'" fill="'+(i%2?'#23374b':'#18283a')+'"><title>'+esc(p.name)+' '+fmt(p.wall_ms)+' ms</title></rect>';});
 for(let i=0;i<=4;i++){let value=ymax*i/4;svg+='<line x1="'+left+'" y1="'+y(value)+'" x2="'+(w-right)+'" y2="'+y(value)+'" stroke="#354658"/><text x="3" y="'+(y(value)+4)+'">'+value.toFixed(1)+'</text>';}
 for(let i=0;i<=5;i++){let time=xmax*i/5;svg+='<text text-anchor="'+(i===5?'end':'start')+'" x="'+x(time)+'" y="'+(h-7)+'">'+(time/1000).toFixed(1)+' s</text>';}
 svg+='<text x="3" y="12">'+esc(unit)+'</text>';
 series.forEach((s,i)=>{let segment=[];const flush=()=>{if(segment.length)svg+='<polyline fill="none" stroke="'+colors[i%colors.length]+'" stroke-width="1.4" points="'+segment.join(' ')+'"/>';segment=[];};rows.forEach(r=>{let v=s[1](r);if(v==null){flush();return;}segment.push(x(r.elapsed_ms)+','+y(v));});flush();});
 svg+='<line class="cursor" y1="'+top+'" y2="'+(h-bottom)+'" stroke="#fff" opacity="0"/></svg><div class="readout">鼠标移入查看采样值</div>';
 box.insertAdjacentHTML('beforeend',svg);
 const el=box.querySelector('svg'),cursor=box.querySelector('.cursor'),out=box.querySelector('.readout');
 el.addEventListener('mousemove',e=>{if(!rows.length)return;let target=(e.clientX-el.getBoundingClientRect().left)/el.getBoundingClientRect().width*w;let time=(target-left)/(w-left-right)*xmax;let r=rows.reduce((a,b)=>Math.abs(a.elapsed_ms-time)<Math.abs(b.elapsed_ms-time)?a:b);cursor.setAttribute('x1',x(r.elapsed_ms));cursor.setAttribute('x2',x(r.elapsed_ms));cursor.setAttribute('opacity',.7);out.innerHTML=fmt(r.elapsed_ms/1000)+' s · '+esc(r.phase)+' · '+series.map(s=>esc(s[0])+': '+fmt(s[1](r))).join(' · ');});
}
document.getElementById('overview').innerHTML='<section><h2>负载汇总</h2><p>CPU 为测量窗口加权均值，100% = 一个逻辑核；tick 为各组独立 p95。先对照 unmatched 的实体成本，再看 condition_miss 的检查成本和 convert 的结算负载。</p>'+table(['步骤','源数','物品','进程 CPU %','服务端 CPU %','原版 p95 ms','runtime p95 ms','总 tick p95 ms','heap 峰值 MiB'],data.runs.map(r=>{const s=r.summary;return [esc(r.phase+' / '+r.step),fmt(r.workload?.sources),esc(r.workload?.source_item??'未记录'),fmt(s.process_cpu_percent_one_core),fmt(s.server_thread_cpu_percent),fmt(s.vanilla_p95_ms),fmt(s.runtime_p95_ms),fmt(s.server_tick_p95_ms),fmt(s.peak_heap_used_mib)];}))+'</section>';
if(data.comparison.length){document.getElementById('comparison').innerHTML='<section><h2>与基线比较</h2><p>增幅 = 当前 / 基线 − 1；负值表示下降，单位 %。</p>'+table(['步骤','runtime p95','总 tick p95','进程 CPU','heap 峰值'],data.comparison.map(c=>c.comparable?[esc(c.step),...Object.values(c.delta_percent).map(v=>fmt(v))]:[esc(c.step),esc(c.reason),'','','']))+'</section>';}
for(const r of data.runs){
 const p=r.performance, node=document.createElement('section');document.getElementById('runs').appendChild(node);
 node.innerHTML='<h2>'+esc(r.phase+' / '+r.step)+' · '+esc(r.verdict)+'</h2><p>采样成本 '+fmt(p.sampling_total_ms)+' ms（单次最大 '+fmt(p.sampling_max_ms)+' ms）；tick 记录 '+fmt(p.tick_recording_total_ms)+' ms；资源样本 '+p.resources.length+'；tick 样本 '+p.ticks.length+'</p>'+
 table(['阶段','起点 ms','墙钟 ms','服务端 CPU ms'],p.phases.map(s=>[esc(s.name),fmt(s.start_ms),fmt(s.wall_ms),fmt(s.server_thread_cpu_ms)]))+
 '<details><summary>环境、负载与统计（'+esc(r.summary.scope)+'）</summary><pre>'+esc(JSON.stringify({environment:r.environment,workload:r.workload,summary:r.summary,window:r.window},null,2))+'</pre></details>';
 chart(node,'CPU 曲线',p.resources,[['JVM 进程 %（单核）',s=>s.process_cpu_percent_one_core],['服务端线程 %',s=>s.server_thread_cpu_percent]],p.phases,p.duration_ms,'%');
 chart(node,'内存曲线',p.resources,[['heap used',s=>s.heap_used_bytes/1048576],['heap committed',s=>s.heap_committed_bytes/1048576],['non-heap used',s=>s.non_heap_used_bytes/1048576]],p.phases,p.duration_ms,'MiB');
 chart(node,'tick 耗时',p.ticks,[['原版',s=>s.vanilla_ms],['IDTW runtime',s=>s.runtime_ms],['原版 + IDTW',s=>s.runtime_ms==null?null:s.vanilla_ms+s.runtime_ms]],p.phases,p.duration_ms,'ms');
 chart(node,'调度分类耗时',p.ticks,p.kind_order.map((name,i)=>[name,s=>s.kind_step_us[i]/1000]),p.phases,p.duration_ms,'ms');
 chart(node,'GC 累计耗时',p.resources,[['GC 累计 ms',s=>s.gc_time_ms]],p.phases,p.duration_ms,'ms');
 node.insertAdjacentHTML('beforeend','<p>GC 次数：'+(p.resources.at(-1)?.gc_count??0)+'；最大 heap 容量：'+fmt((p.resources.at(-1)?.heap_max_bytes??0)/1048576)+' MiB</p>');
}
</script></html>'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--compare-to', type=Path)
    args = parser.parse_args()
    runs = read_runs(args.directory)
    errors = validate(runs)
    for run in runs:
        run['summary'] = summary(run)
    compared = comparison(runs, read_runs(args.compare_to)) if args.compare_to else []
    payload = dict(format='idtw-performance-report-v1', runs=runs, comparison=compared, errors=errors)
    (args.directory / 'performance-summary.json').write_text(json.dumps(
        dict(format=payload['format'], results=[{key: value for key, value in run.items() if key != 'performance'} for run in runs],
             comparison=compared, errors=errors), ensure_ascii=False, indent=2), encoding='utf-8')
    export_csv(args.directory, runs)
    encoded = json.dumps(payload, ensure_ascii=False).replace('<', '\\u003c')
    (args.directory / 'performance.html').write_text(PAGE.replace('__DATA__', encoded), encoding='utf-8')
    for error in errors:
        print(f'[performance] FAIL {error}')
    print(f'[performance] steps={len(runs)} errors={len(errors)} report={args.directory / "performance.html"}')
    return int(bool(errors))


if __name__ == '__main__':
    raise SystemExit(main())
