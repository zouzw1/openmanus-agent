import os
from docx import Document
from docx.shared import Pt, RGBColor
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

# 读取Markdown文件内容
def read_md_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        return f.read()

# 创建格式化的Word文档
def create_formatted_docx(md_content, output_path):
    doc = Document()
    
    # 设置默认字体
    style = doc.styles['Normal']
    font = style.font
    font.name = 'SimSun'  # 中文字体
    font.size = Pt(12)
    
    # 处理Markdown内容并添加到文档
    lines = md_content.split('\n')
    for line in lines:
        line = line.strip()
        if not line:
            continue
        
        # 处理标题（#、##、###）
        if line.startswith('### '):
            p = doc.add_paragraph(line[4:], 'Heading 3')
            p.runs[0].font.size = Pt(14)
        elif line.startswith('## '):
            p = doc.add_paragraph(line[3:], 'Heading 2')
            p.runs[0].font.size = Pt(16)
        elif line.startswith('# '):
            p = doc.add_paragraph(line[2:], 'Heading 1')
            p.runs[0].font.size = Pt(18)
        else:
            # 普通段落
            p = doc.add_paragraph(line)
            
            # 设置中英文字体
            for run in p.runs:
                run.font.name = 'SimSun'
                run._element.rPr.rFonts.set(qn('w:eastAsia'), 'SimSun')
                run.font.size = Pt(12)
    
    # 保存文档
    doc.save(output_path)

# 主程序
if __name__ == '__main__':
    # 读取Markdown文件
    md_content = read_md_file('travel_plan.md')
    
    # 创建格式化的Word文档
    create_formatted_docx(md_content, '南京7日旅行计划.docx')
    
    print('Word文档已成功生成：南京7日旅行计划.docx')