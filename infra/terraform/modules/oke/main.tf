data "oci_containerengine_cluster_option" "all" {
  cluster_option_id              = "all"
  compartment_id                 = var.compartment_id
  should_list_all_patch_versions = true
}

data "oci_core_shapes" "selected_node_shape" {
  compartment_id      = var.compartment_id
  availability_domain = var.availability_domains[0].name
  shape               = var.node_shape
}

locals {
  kubernetes_version_candidates = sort([
    for version in data.oci_containerengine_cluster_option.all.kubernetes_versions : format(
      "%05d.%05d.%05d|%s",
      tonumber(regex("^v?([0-9]+)\\.([0-9]+)\\.([0-9]+)$", version)[0]),
      tonumber(regex("^v?([0-9]+)\\.([0-9]+)\\.([0-9]+)$", version)[1]),
      tonumber(regex("^v?([0-9]+)\\.([0-9]+)\\.([0-9]+)$", version)[2]),
      version
    )
    if can(regex("^v?[0-9]+\\.[0-9]+\\.[0-9]+$", version))
  ])

  latest_kubernetes_version = split("|", local.kubernetes_version_candidates[length(local.kubernetes_version_candidates) - 1])[1]

  effective_kubernetes_version      = var.kubernetes_version != "" ? var.kubernetes_version : local.latest_kubernetes_version
  effective_kubernetes_version_no_v = trimprefix(lower(local.effective_kubernetes_version), "v")

  selected_node_shape_processor_description = lower(coalesce(try(data.oci_core_shapes.selected_node_shape.shapes[0].processor_description, null), ""))

  detected_node_pool_os_arch = (
    strcontains(local.selected_node_shape_processor_description, "ampere") ||
    strcontains(local.selected_node_shape_processor_description, "arm")
  ) ? "AARCH64" : "X86_64"

  effective_node_pool_os_arch    = var.node_pool_os_arch_override != "" ? var.node_pool_os_arch_override : local.detected_node_pool_os_arch
  selected_node_shape_is_flex    = try(data.oci_core_shapes.selected_node_shape.shapes[0].is_flexible, false)
  worker_node_container_platform = local.effective_node_pool_os_arch == "AARCH64" ? "linux/arm64" : "linux/amd64"
}

data "oci_containerengine_node_pool_option" "all" {
  node_pool_option_id            = "all"
  compartment_id                 = var.compartment_id
  node_pool_k8s_version          = local.effective_kubernetes_version
  node_pool_os_arch              = local.effective_node_pool_os_arch
  node_pool_os_type              = var.node_pool_os_type
  should_list_all_patch_versions = true
}

locals {
  node_image_version_token = "-oke-${local.effective_kubernetes_version_no_v}-"

  node_image_candidates = [
    for source in data.oci_containerengine_node_pool_option.all.sources : source
    if source.source_type == "IMAGE"
    && strcontains(lower(source.source_name), local.node_image_version_token)
    && (var.node_image_name_filter == "" || strcontains(lower(source.source_name), lower(var.node_image_name_filter)))
  ]
}

data "oci_core_shapes" "node_image_compatibility" {
  for_each = {
    for source in local.node_image_candidates : source.image_id => source
  }

  compartment_id      = var.compartment_id
  availability_domain = var.availability_domains[0].name
  image_id            = each.value.image_id
  shape               = var.node_shape
}

locals {
  compatible_node_image_candidates = [
    for source in local.node_image_candidates : source
    if length(data.oci_core_shapes.node_image_compatibility[source.image_id].shapes) > 0
  ]

  compatible_node_image_sort_keys = sort([
    for source in local.compatible_node_image_candidates : "${source.source_name}|${source.image_id}"
  ])

  auto_selected_node_image_name = length(local.compatible_node_image_sort_keys) > 0 ? split("|", local.compatible_node_image_sort_keys[length(local.compatible_node_image_sort_keys) - 1])[0] : ""
  auto_selected_node_image_id   = length(local.compatible_node_image_sort_keys) > 0 ? split("|", local.compatible_node_image_sort_keys[length(local.compatible_node_image_sort_keys) - 1])[1] : ""

  selected_node_image_id   = var.node_image_id != "" ? var.node_image_id : local.auto_selected_node_image_id
  selected_node_image_name = var.node_image_id != "" ? "custom-image-id:${var.node_image_id}" : local.auto_selected_node_image_name
}

resource "oci_containerengine_cluster" "this" {
  compartment_id     = var.compartment_id
  kubernetes_version = local.effective_kubernetes_version
  name               = "${var.project_name}-oke"
  type               = "BASIC_CLUSTER"
  vcn_id             = var.vcn_id
  freeform_tags      = var.freeform_tags

  cluster_pod_network_options {
    cni_type = "FLANNEL_OVERLAY"
  }

  endpoint_config {
    is_public_ip_enabled = true
    subnet_id            = var.control_plane_subnet_id
  }

  options {
    service_lb_subnet_ids = [var.lb_subnet_id]

    kubernetes_network_config {
      pods_cidr     = var.pods_cidr
      services_cidr = var.services_cidr
    }
  }

  lifecycle {
    precondition {
      condition     = contains(data.oci_containerengine_cluster_option.all.kubernetes_versions, local.effective_kubernetes_version)
      error_message = "kubernetes_version=${local.effective_kubernetes_version} is not available for this OCI region/compartment. Leave kubernetes_version empty to auto-select the latest version."
    }
  }
}

resource "oci_containerengine_node_pool" "workers" {
  cluster_id         = oci_containerengine_cluster.this.id
  compartment_id     = var.compartment_id
  kubernetes_version = local.effective_kubernetes_version
  name               = "${var.project_name}-workers"
  node_shape         = var.node_shape
  ssh_public_key     = var.ssh_public_key != "" ? var.ssh_public_key : null
  freeform_tags      = var.freeform_tags

  initial_node_labels {
    key   = "workload"
    value = var.project_name
  }

  node_config_details {
    size                                = var.node_count
    is_pv_encryption_in_transit_enabled = false

    dynamic "placement_configs" {
      for_each = var.availability_domains
      content {
        availability_domain = placement_configs.value.name
        subnet_id           = var.worker_subnet_id
      }
    }
  }

  dynamic "node_shape_config" {
    for_each = local.selected_node_shape_is_flex ? [1] : []

    content {
      ocpus         = var.node_ocpus
      memory_in_gbs = var.node_memory_in_gbs
    }
  }

  node_source_details {
    source_type = "IMAGE"
    image_id    = local.selected_node_image_id
  }

  lifecycle {
    precondition {
      condition     = length(local.kubernetes_version_candidates) > 0
      error_message = "No valid OKE Kubernetes patch versions were returned by OCI for this region/compartment."
    }

    precondition {
      condition     = length(data.oci_core_shapes.selected_node_shape.shapes) > 0
      error_message = "The configured node_shape was not found in the selected availability domain: ${var.node_shape}."
    }

    precondition {
      condition     = local.selected_node_image_id != ""
      error_message = "No compatible OKE worker image was found for node_shape=${var.node_shape}, os_arch=${local.effective_node_pool_os_arch}, os_type=${var.node_pool_os_type}, kubernetes_version=${local.effective_kubernetes_version}. Try another supported node_shape, clear node_image_name_filter, or set node_image_id manually."
    }
  }

  depends_on = [oci_containerengine_cluster.this]
}
