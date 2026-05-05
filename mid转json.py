import mido
import json

# 游戏音符映射字典
midi_to_nikki = {
    48: "B1", 50: "B2", 52: "B3", 53: "B4", 55: "B5", 57: "B6", 59: "B7",
    60: "M1", 62: "M2", 64: "M3", 65: "M4", 67: "M5", 69: "M6", 71: "M7",
    72: "T1", 74: "T2", 76: "T3", 77: "T4", 79: "T5", 81: "T6", 83: "T7"
}

def force_map_to_nikki(pitch):
    """
    暴力适配核心：八度折叠与黑键白键化，确保不漏任何一个音
    """
    while pitch < 48: pitch += 12
    while pitch > 83: pitch -= 12
    mod_12 = pitch % 12
    if mod_12 in [1, 3, 6, 8, 10]: pitch -= 1
    return pitch

def convert_midi_to_json_v5(midi_file_path, output_json_path):
    print(f"🎵 正在生成【千手观音·独立延音版】乐谱: {midi_file_path} ...")
    try:
        mid = mido.MidiFile(midi_file_path)
    except Exception as e:
        print(f"❌ 读取失败，请检查文件路径: {e}")
        return

    active_notes = {}
    raw_events = []
    current_time_ms = 0

    # ==========================================
    # 1. 提取所有音符的绝对时间 (按下和抬起)
    # ==========================================
    for msg in mid:
        current_time_ms += int(msg.time * 1000)
        
        # 过滤架子鼓
        if hasattr(msg, 'channel') and msg.channel == 9:
            continue

        if msg.type == 'note_on' and msg.velocity > 15:
            # 暴力映射音高
            mapped_pitch = force_map_to_nikki(msg.note)
            active_notes[msg.note] = {'pitch': mapped_pitch, 'start': current_time_ms}
            
        elif msg.type == 'note_off' or (msg.type == 'note_on' and msg.velocity <= 15):
            if msg.note in active_notes:
                note_data = active_notes.pop(msg.note)
                # 过滤极短的杂音误触
                if current_time_ms - note_data['start'] > 10:
                    raw_events.append({
                        'pitch': note_data['pitch'],
                        'start': note_data['start'],
                        'end': current_time_ms
                    })

    if not raw_events:
        print("❌ 未提取到任何有效音符！")
        return

    raw_events.sort(key=lambda x: x['start'])

    # ==========================================
    # 2. 和弦聚类 (容错 15ms)
    # ==========================================
    chords = []
    current_chord = [raw_events[0]]
    for note in raw_events[1:]:
        if note['start'] - current_chord[0]['start'] <= 15:
            current_chord.append(note)
        else:
            chords.append(current_chord)
            current_chord = [note]
    if current_chord:
        chords.append(current_chord)

    # ==========================================
    # 3. 导出包含专属 durations 数组的 JSON
    # ==========================================
    json_score = []
    for i in range(len(chords)):
        chord_group = chords[i]
        
        # 【核心魔法】：使用字典来去重，并记录每个琴键的最长按压时间
        note_durations_dict = {}
        for n in chord_group:
            note_name = midi_to_nikki[n['pitch']]
            dur = n['end'] - n['start']
            dur = max(50, min(dur, 800)) # 安全锁：时长限制在 50~800ms 之间
            
            # 如果八度折叠导致两个不同的音映射到了同一个琴键上，取延音最长的那个
            if note_name in note_durations_dict:
                note_durations_dict[note_name] = max(note_durations_dict[note_name], dur)
            else:
                note_durations_dict[note_name] = dur

        # 拆分为两个一一对应的数组
        note_names = list(note_durations_dict.keys())
        durations_list = list(note_durations_dict.values())

        # 计算到下一组动作的延迟时间
        if i < len(chords) - 1:
            delay = chords[i+1][0]['start'] - chord_group[0]['start']
        else:
            delay = max(durations_list) if durations_list else 400
            
        delay = max(10, delay) # 防止死循环卡死

        # 封装进目标格式
        json_score.append({
            "notes": note_names,
            "durations": durations_list, # <--- 这里变成了数组！
            "delay": delay
        })

    # ==========================================
    # 4. 保存文件
    # ==========================================
    with open(output_json_path, 'w', encoding='utf-8') as f:
        json.dump(json_score, f, ensure_ascii=False, indent=2)

    print(f"✅ 转换完成！共生成 {len(json_score)} 组动作。")
    print(f"✅ 乐谱已完美支持【多指错位断奏】（独立时值）。")

if __name__ == "__main__":
    # 请确保你的电脑同级目录下有一个叫 song.mid 的文件
    convert_midi_to_json_v5("song.mid", "song.json")