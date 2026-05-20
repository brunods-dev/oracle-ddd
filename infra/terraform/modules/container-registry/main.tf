resource "oci_artifacts_container_repository" "backend" {
  compartment_id = var.compartment_id
  display_name   = "${var.project_name}/backend"
  is_immutable   = var.is_immutable
  is_public      = var.is_public
  freeform_tags  = var.freeform_tags
}

resource "oci_artifacts_container_repository" "frontend" {
  compartment_id = var.compartment_id
  display_name   = "${var.project_name}/frontend"
  is_immutable   = var.is_immutable
  is_public      = var.is_public
  freeform_tags  = var.freeform_tags
}
