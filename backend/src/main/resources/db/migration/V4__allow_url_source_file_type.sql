ALTER TABLE sources DROP CONSTRAINT sources_file_type_check;
ALTER TABLE sources ADD CONSTRAINT sources_file_type_check
    CHECK (file_type IN ('PDF', 'DOCX', 'PPTX', 'TXT', 'MD', 'URL'));
