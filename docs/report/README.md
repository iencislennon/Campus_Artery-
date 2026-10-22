Regenerate the PDF (needs Google Chrome and internet access for the Mermaid script):

    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --no-pdf-header-footer \
      --virtual-time-budget=30000 --print-to-pdf=docs/SDU_Bagdar_Report.pdf file://$PWD/docs/report/report.html
