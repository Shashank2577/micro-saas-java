import sys, json

try:
    d = json.load(sys.stdin)
    if "sessions" not in d:
        print("No sessions found or error:", d)
        sys.exit(0)
    
    count = 0
    for s in d["sessions"]:
        s_str = str(s)
        if "micro-saas-java" in s_str or "micro-saas-applications-java" in s_str:
            state = s.get("state", "UNKNOWN")
            title = s.get("title", "No Title")
            name = s.get("name", "Unknown ID")
            print(f"  {state}: {title} ({name})")
            count += 1
    
    if count == 0:
        print("  No sessions found for this repo.")
except Exception as e:
    print("Error parsing JSON:", e)