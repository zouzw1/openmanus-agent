const { Document, Packer, Paragraph, TextRun, HeadingLevel } = require('docx');

const doc = new Document({
  styles: {
    default: { document: { run: { font: "Arial", size: 24 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 32, bold: true, font: "Arial" },
        paragraph: { spacing: { before: 240, after: 240 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 28, bold: true, font: "Arial" },
        paragraph: { spacing: { before: 180, after: 180 }, outlineLevel: 1 } },
    ]
  },
  sections: [{
    properties: {
      page: {
        size: {
          width: 12240,   // US Letter width (8.5 inches)
          height: 15840   // US Letter height (11 inches)
        },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } // 1 inch margins
      }
    },
    children: [
      new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text: "Whispers of the Wild", bold: true, size: 32 })] }),
      new Paragraph({ children: [new TextRun({ text: "", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "Beneath the ancient oak's broad shade,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "Where dappled sunlight softly played,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "The wind weaves tales through rustling leaves,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "A symphony the forest breathes.", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "The river sings its silver song,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "Where crystal waters flow along,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "Past mossy stones and water-worn grace,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "Reflecting heaven's boundless space.", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "The mountains stand in silent might,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "Bathed in the moon's ethereal light,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "Their snow-capped peaks like dreams take flight,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "Guardians of the endless night.", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "So nature speaks in gentle ways,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "Through changing seasons' subtle plays,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "A timeless wisdom, deep and vast,", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "That holds the fragile world embraced.", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "", size: 24 })] }),
      new Paragraph({ children: [new TextRun({ text: "— A Poem of Earth's Enduring Grace", size: 24, italic: true })] }),
    ]
  }]
});

Packer.toBuffer(doc).then(buffer => {
  // This would write the file in a Node.js environment
  // For our purposes, we'll use the workspace system
  console.log('Document created successfully');
});