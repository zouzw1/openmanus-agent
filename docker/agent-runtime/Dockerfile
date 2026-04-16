FROM python:3.12-slim

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    pandoc \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 安装 uv
RUN curl -LsSf https://astral.sh/uv/install.sh | sh

# 预装 Python 文档处理库
RUN /root/.local/bin/uv pip install --system \
    python-docx \
    pypdf \
    reportlab \
    pdfplumber \
    python-pptx

# 预装 Python 数据处理库
RUN /root/.local/bin/uv pip install --system \
    pandas \
    numpy \
    matplotlib \
    openpyxl

# 设置工作目录
WORKDIR /workspace

# 设置 PATH（包含 uv）
ENV PATH="/root/.local/bin:$PATH"
