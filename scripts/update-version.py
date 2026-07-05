import sys
import re
import datetime
import os

def update_docs(file_path, new_version, notes=None):
    if not os.path.exists(file_path):
        print(f"Warning: {file_path} not found.")
        return notes

    with open(file_path, 'r') as f:
        content = f.read()
    
    date_str = datetime.date.today().isoformat()
    
    # Special handling for CHANGELOG.md (has [Unreleased])
    if "CHANGELOG.md" in file_path:
        pattern = r'(## \[Unreleased\])(.*?)(\n## \[|$)'
        match = re.search(pattern, content, re.DOTALL)
        
        if match:
            unreleased_header = match.group(1)
            found_notes = match.group(2).strip()
            trailing = match.group(3)
            
            if not found_notes:
                return None
            
            # Use provided notes if any, else use found notes
            effective_notes = notes if notes else found_notes
            
            new_entry = f"\n\n## [{new_version}] - {date_str}\n\n{effective_notes}\n"
            
            # New content: Keep ## [Unreleased] empty, then add the new version entry
            # We use \n\n after unreleased_header to keep it separated
            new_content = content[:match.start()] + unreleased_header + new_entry + trailing + content[match.end():]
            
            with open(file_path, 'w') as f:
                f.write(new_content)
            return effective_notes
    
    # Handling for RELEASE_NOTES.md (just prepend)
    elif "RELEASE_NOTES.md" in file_path:
        if notes:
            new_entry = f"## [{new_version}] - {date_str}\n{notes}\n\n"
            with open(file_path, 'w') as f:
                f.write(new_entry + content)
    
    return notes

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: update-version.py <new_version>")
        sys.exit(1)
        
    new_version = sys.argv[1]
    
    # Root files
    notes = update_docs("../CHANGELOG.md", new_version)
    if notes:
        update_docs("../RELEASE_NOTES.md", new_version, notes)
        
        # Asset files
        update_docs("../ultrasonic/src/main/assets/CHANGELOG.md", new_version, notes)
        update_docs("../ultrasonic/src/main/assets/RELEASE_NOTES.md", new_version, notes)
