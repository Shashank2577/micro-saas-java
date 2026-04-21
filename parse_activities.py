import sys, json

try:
    d = json.load(sys.stdin)
    activities = d.get("activities", [])
    for a in activities[-10:]:
        time = a.get("createTime", "Unknown")
        desc = a.get("description", "No description")
        print(f"[{time}] {desc}")
except Exception as e:
    print("Error:", e)