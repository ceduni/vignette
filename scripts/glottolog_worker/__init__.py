"""External Glottolog pipeline worker (V1).

Polls PostgreSQL for admin settings and manual update requests, then runs the
Zenodo → CLDF → language import pipeline. Operational state lives in
``glottolog_pipeline_state``; Java owns ``glottolog_admin_settings`` only.
"""

__version__ = "1.0.0"
