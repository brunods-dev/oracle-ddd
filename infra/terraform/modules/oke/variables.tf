variable "project_name" {
  type = string
}

variable "compartment_id" {
  type = string
}

variable "vcn_id" {
  type = string
}

variable "control_plane_subnet_id" {
  type = string
}

variable "worker_subnet_id" {
  type = string
}

variable "lb_subnet_id" {
  type = string
}

variable "availability_domains" {
  description = "OCI availability domains returned by the identity data source."
  type = list(object({
    name = string
  }))
}

variable "kubernetes_version" {
  type    = string
  default = ""
}

variable "node_count" {
  type = number
}

variable "node_shape" {
  type = string
}

variable "node_ocpus" {
  type = number
}

variable "node_memory_in_gbs" {
  type = number
}

variable "node_image_id" {
  type    = string
  default = ""
}

variable "node_image_name_filter" {
  type    = string
  default = ""
}

variable "ssh_public_key" {
  type    = string
  default = ""
}

variable "pods_cidr" {
  type = string
}

variable "services_cidr" {
  type = string
}

variable "freeform_tags" {
  type    = map(string)
  default = {}
}

variable "node_pool_os_type" {
  description = "Operating system family used to query OKE worker node images. OCI accepts OL7, OL8 and UBUNTU."
  type        = string
  default     = "OL8"

  validation {
    condition     = contains(["OL7", "OL8", "UBUNTU"], var.node_pool_os_type)
    error_message = "node_pool_os_type must be one of: OL7, OL8, UBUNTU."
  }
}

variable "node_pool_os_arch_override" {
  description = "Optional override for OKE worker image architecture. Leave empty to detect from node_shape metadata. Valid values: X86_64 or AARCH64."
  type        = string
  default     = ""

  validation {
    condition     = contains(["", "X86_64", "AARCH64"], var.node_pool_os_arch_override)
    error_message = "node_pool_os_arch_override must be empty, X86_64, or AARCH64."
  }
}
