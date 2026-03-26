import sys
import os
from docx import Document
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

# Read markdown file
def read_markdown_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        return f.read()

# Simple markdown to docx converter
def markdown_to_docx(md_content, output_path):
    doc = Document()
    
    # Split content by lines
    lines = md_content.split('\n')
    
    for line in lines:
        line = line.strip()
        
        # Handle headers
        if line.startswith('# '):
            paragraph = doc.add_heading(line[2:], level=1)
        elif line.startswith('## '):
            paragraph = doc.add_heading(line[3:], level=2)
        elif line.startswith('### '):
            paragraph = doc.add_heading(line[4:], level=3)
        elif line.startswith('#### '):
            paragraph = doc.add_heading(line[5:], level=4)
        # Handle lists
        elif line.startswith('- ') or line.startswith('* '):
            paragraph = doc.add_paragraph(line[2:], style='List Bullet')
        elif line.startswith('1. '):
            paragraph = doc.add_paragraph(line[3:], style='List Number')
        # Handle code blocks
        elif line.startswith('```'):
            continue  # Skip code block markers
        # Handle regular paragraphs
        elif line:
            paragraph = doc.add_paragraph(line)
        # Handle empty lines
        else:
            doc.add_paragraph()
    
    # Save document
    doc.save(output_path)

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print('Usage: python convert_md_to_docx.py <input_md_file> <output_docx_file>')
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    if not os.path.exists(input_file):
        print(f'Error: Input file {input_file} does not exist')
        sys.exit(1)
    
    md_content = read_markdown_file(input_file)
    markdown_to_docx(md_content, output_file)
    print(f'Successfully converted {input_file} to {output_file}')