$input = "Admin6459VkBook2024"
$bytes = [System.Text.Encoding]::UTF8.GetBytes($input)
$hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
$hex = [System.BitConverter]::ToString($hash) -replace '-', ''
$hex.ToLower()





























