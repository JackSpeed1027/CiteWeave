CREATE TABLE source_pdf_pages (
    source_id bigint NOT NULL REFERENCES sources(id),
    page_number integer NOT NULL CHECK (page_number BETWEEN 1 AND 500),
    content text NOT NULL CHECK (length(content) > 0),
    PRIMARY KEY (source_id, page_number)
);
