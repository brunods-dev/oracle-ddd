output "backend_repository_id" {
  value = oci_artifacts_container_repository.backend.id
}

output "frontend_repository_id" {
  value = oci_artifacts_container_repository.frontend.id
}

output "backend_repository_url" {
  value = "${var.ocir_endpoint}/${var.namespace}/${oci_artifacts_container_repository.backend.display_name}"
}

output "frontend_repository_url" {
  value = "${var.ocir_endpoint}/${var.namespace}/${oci_artifacts_container_repository.frontend.display_name}"
}

output "ocir_endpoint" {
  value = var.ocir_endpoint
}

output "ocir_namespace" {
  value = var.namespace
}
