variable "project_name" {
  description = "Short project prefix used in resource names."
  type        = string
  default     = "oracle-ddd-demo"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,30}$", var.project_name))
    error_message = "Use 3-31 chars, lowercase letters, numbers and hyphens, starting with a letter."
  }
}

variable "region" {
  description = "OCI region, for example sa-saopaulo-1, us-ashburn-1, eu-frankfurt-1."
  type        = string
}

variable "tenancy_ocid" {
  description = "OCI tenancy OCID. Required by the OCI provider."
  type        = string
}

variable "compartment_ocid" {
  description = "OCI compartment OCID where all demo resources will be created."
  type        = string
}

variable "auth" {
  description = "OCI provider auth mode. Use SecurityToken for OCI CLI session auth, or APIKey for API key auth."
  type        = string
  default     = "SecurityToken"
}

variable "config_file_profile" {
  description = "OCI CLI config profile."
  type        = string
  default     = "DEFAULT"
}

variable "user_ocid" {
  description = "OCI user OCID. Required only for APIKey auth."
  type        = string
  default     = null
}

variable "fingerprint" {
  description = "API key fingerprint. Required only for APIKey auth."
  type        = string
  default     = null
}

variable "private_key_path" {
  description = "API private key path. Required only for APIKey auth."
  type        = string
  default     = null
}

variable "private_key_password" {
  description = "API private key password, when applicable."
  type        = string
  default     = null
  sensitive   = true
}

variable "vcn_cidr" {
  description = "VCN CIDR."
  type        = string
  default     = "10.0.0.0/16"
}

variable "control_plane_subnet_cidr" {
  description = "Public regional subnet for the OKE Kubernetes API endpoint."
  type        = string
  default     = "10.0.0.0/24"
}

variable "worker_subnet_cidr" {
  description = "Public regional subnet for OKE worker nodes."
  type        = string
  default     = "10.0.10.0/24"
}

variable "lb_subnet_cidr" {
  description = "Public regional subnet for OCI load balancers."
  type        = string
  default     = "10.0.20.0/24"
}

variable "kubernetes_version" {
  description = "OKE Kubernetes version. Leave empty to let Terraform use the first version returned by OCI for the region. Pin it for repeatable environments."
  type        = string
  default     = ""
}

variable "node_count" {
  description = "Number of worker nodes."
  type        = number
  default     = 2
}

variable "node_shape" {
  description = "Worker node shape."
  type        = string
  default     = "VM.Standard.E4.Flex"
}

variable "node_ocpus" {
  description = "OCPUs per worker node for Flex shapes."
  type        = number
  default     = 2
}

variable "node_memory_in_gbs" {
  description = "Memory per worker node for Flex shapes."
  type        = number
  default     = 16
}

variable "node_image_id" {
  description = "Optional OKE worker image OCID override. Leave empty to auto-select from OCI node pool options."
  type        = string
  default     = ""
}

variable "node_image_name_filter" {
  description = "Optional filter for auto-selected worker image source name. Keep empty to accept the first OCI option."
  type        = string
  default     = ""
}

variable "ssh_public_key" {
  description = "Optional SSH public key for worker nodes."
  type        = string
  default     = ""
}

variable "pods_cidr" {
  description = "Cluster pod CIDR for Flannel overlay."
  type        = string
  default     = "10.244.0.0/16"
}

variable "services_cidr" {
  description = "Cluster service CIDR."
  type        = string
  default     = "10.96.0.0/16"
}

variable "freeform_tags" {
  description = "Extra OCI freeform tags."
  type        = map(string)
  default     = {}
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

variable "mysql_admin_username" {
  description = "Administrative username for MySQL HeatWave."
  type        = string
  default     = "admin"
}

variable "mysql_admin_password" {
  description = "Administrative password for MySQL HeatWave."
  type        = string
  sensitive   = true
}

variable "mysql_shape_name" {
  description = "MySQL DB System shape."
  type        = string
  default     = "MySQL.2"
}

variable "mysql_data_storage_size_in_gb" {
  description = "Initial MySQL DB System data storage size in GB."
  type        = number
  default     = 50
}

variable "mysql_auto_expand_storage" {
  description = "Enable automatic storage expansion."
  type        = bool
  default     = true
}

variable "mysql_max_storage_size_in_gbs" {
  description = "Maximum storage size when automatic expansion is enabled."
  type        = number
  default     = 100
}

variable "mysql_port" {
  description = "MySQL TCP port."
  type        = number
  default     = 3306
}

variable "mysql_port_x" {
  description = "MySQL X Protocol port."
  type        = number
  default     = 33060
}

variable "mysql_database_name" {
  description = "Application database name."
  type        = string
  default     = "copa_ticketing_demo"
}

variable "mysql_enable_heatwave_cluster" {
  description = "Whether to attach a HeatWave cluster to the MySQL DB System."
  type        = bool
  default     = true
}

variable "mysql_heatwave_shape_name" {
  description = "HeatWave cluster shape."
  type        = string
  default     = "HeatWave.32GB"
}

variable "mysql_heatwave_cluster_size" {
  description = "Number of HeatWave cluster nodes."
  type        = number
  default     = 1
}