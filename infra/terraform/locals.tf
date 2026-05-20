locals {
  common_tags = merge(var.freeform_tags, {
    project    = var.project_name
    managed_by = "terraform"
    purpose    = "demo"
  })
}
